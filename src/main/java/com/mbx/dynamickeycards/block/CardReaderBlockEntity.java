package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DKConfig;
import com.mbx.dynamickeycards.item.EstateKeycardItem;
import com.mbx.dynamickeycards.item.GoldenKeycardItem;
import com.mbx.dynamickeycards.item.KeycardItem;
import com.mbx.dynamickeycards.registry.DKBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * State for a card reader: the owner it bound to when placed, the set of registered
 * keycard ids, and whether register mode is active. Fully synced to clients (update tag +
 * data packet) so both sides resolve interactions the same way. The {@link LinkDeviceBlockEntity}
 * half (signal mode, frequency slots, signal length) is shared with the motion sensors - see
 * that interface for what's generic here versus reader-specific.
 */
public class CardReaderBlockEntity extends BlockEntity implements LinkDeviceBlockEntity, SignalSource {

    /**
     * How long a card acceptance is reported to the wireless system as a fixed short blip,
     * independent of this reader's own held-open duration - now used only while a
     * {@link BoundReaderMode#SIMULTANEOUS}-bound sensor drives this reader (see
     * {@link #simultaneousBlipActiveUntilGameTime}/{@link #markSignalSourceTriggered}): that mode's
     * whole point is that the reader manages its own release independently of the sensor, so it can
     * end up re-triggering repeatedly (a "possibly-repeating pulse train" - see
     * {@code AdvancedSensorBlockEntity#tickBoundToReader}'s own doc) for as long as the sensor keeps
     * detecting; mirroring the reader's full held-open MODE for each of those individual re-triggers
     * would just mean the receiver blindly repeats the same pulse train, whereas one short blip per
     * re-trigger reads as what it actually is - a series of distinct events, not one long hold.
     * Direct taps and a {@link BoundReaderMode#READER_ONLY}-bound sensor still get the full
     * MODE-based mirror (see {@link #getSignalSourceStrength}) - only SIMULTANEOUS's own repeated,
     * independently-timed re-triggers get this treatment.
     */
    private static final int MOMENTARY_TICKS = 4;
    /**
     * How far past "now" a {@link BoundReaderMode#SENSOR_CENTRIC_SIMULTANEOUS}-bound sensor's raw
     * detection overrides this reader's own MODE-based wireless broadcast - see
     * {@link #publishSensorRawSignal}. Also reused as the refresh margin for
     * {@link #simultaneousBlipActiveUntilGameTime} - same reasoning, same
     * {@code AdvancedSensorBlockEntity#HOLD_BUFFER_TICKS} precedent.
     */
    private static final int SENSOR_OVERRIDE_BUFFER_TICKS = 2;

    /** Stable identity for this reader in the wireless system's {@link DeviceIndex} - see {@link #getDeviceId}. */
    private UUID deviceId = UUID.randomUUID();
    /** Game time until which {@link #getSignalSourceStrength} reports 15 while {@link #simultaneousBlipActiveUntilGameTime} is active; see {@link #MOMENTARY_TICKS}. */
    private long momentaryUntilGameTime = -1;
    /**
     * Game time until which {@link #getSignalSourceStrength} mirrors {@link #sensorRawOverrideStrength}
     * instead of this reader's own MODE-based accept state - self-expiring (refreshed every tick a
     * driving sensor calls {@link #publishSensorRawSignal}) rather than a sticky flag, so a sensor
     * that stops driving this reader for any reason (unbound, destroyed, switched to a different
     * {@link BoundReaderMode}) falls back to this reader's own MODE-based broadcast within a
     * couple ticks instead of leaving it stuck ignoring its own accept pulses forever.
     */
    private long sensorRawOverrideUntilGameTime = -1;
    /** The value {@link #getSignalSourceStrength} reports while {@link #sensorRawOverrideUntilGameTime} is still in the future. */
    private int sensorRawOverrideStrength;
    /**
     * Game time until which {@link #getSignalSourceStrength} uses {@link #momentaryUntilGameTime}'s
     * short-blip window instead of falling back to this reader's own MODE-based accept state, while
     * a {@link BoundReaderMode#SIMULTANEOUS}-bound sensor keeps this reader independently
     * re-triggering - self-expiring the same way (and for the same reason) as
     * {@link #sensorRawOverrideUntilGameTime}, refreshed every tick by
     * {@link #keepSimultaneousBlipModeActive} regardless of whether that sensor is actively
     * re-triggering this exact tick, so the gap between two of its independent re-triggers still
     * reads as 0 rather than accidentally falling back to this reader's own (still physically held
     * open) MODE.
     */
    private long simultaneousBlipActiveUntilGameTime = -1;
    /**
     * Legacy {@link BlockPos}-based links read from a pre-0.1.8 save, held here only until
     * {@link #onLoad} can resolve each one to the linked reader's own {@link #deviceId} (which
     * requires the level, not available yet during {@link #loadAdditional}) - see {@link #onLoad}
     * for the resolution itself and its limits.
     */
    private List<BlockPos> legacyLinkedReaderPositions = List.of();

    @Nullable
    private UUID owner;
    private final Set<UUID> registeredCards = new HashSet<>();
    /** Own keys of individually blocked cards — the block always beats the allow list. */
    private final Set<UUID> blockedCards = new HashSet<>();
    /**
     * Other readers this one shares registered/blocked cards with (see {@link #accepts}) - each
     * entry added on both ends when one reader is placed while linked to the other (see
     * {@code LinkedReaderBlockItem}/{@code CardReaderBlock#setPlacedBy}). Everything else about
     * a reader - owner, mode/frequency, pulse - stays independent; this only affects which cards
     * a tap here accepts. Identifies each linked reader by its {@link #deviceId} rather than a
     * raw {@link BlockPos} - see {@link DeviceIndex}'s own doc for why.
     *
     * <p>A set, not a single link: the whole connected group - however many readers, in whatever
     * shape (chain, star, even a loop) - shares one combined accept list, so {@link #accepts}
     * walks the full reachable set (see {@link #linkedGroup()}), not just this field's own
     * direct entries.
     */
    private final Set<UUID> linkedReaderIds = new HashSet<>();
    private boolean registerMode;
    /**
     * How long a destructive action's confirming second click stays armed before it has to be
     * re-started from scratch.
     */
    /**
     * Game time {@link #armResetPending()} last ran; {@code -1} while no golden-keycard full
     * reset is awaiting its confirming second click. Not persisted (like {@link #pulseStartGameTime}
     * below) - a stale confirmation across a reload safely reads as expired.
     */
    private final WrenchPickupState resetPending = new WrenchPickupState();
    private final WrenchPickupState wrenchPickup = new WrenchPickupState();
    /**
     * Per-reader accept-pulse override in ticks; {@code -1} means "use the config
     * default".
     */
    private int signalLength = -1;
    /**
     * Game time the current accept pulse began. {@link CardReaderBlock#tickPulseTimeout} checks
     * this against the current pulse length every tick (rather than a one-shot scheduled tick,
     * which a level only ever keeps one of per position — see that method's own comment) so a
     * length change made mid-pulse takes effect immediately instead of only on the next press.
     * {@code -1} while no pulse is running. Deliberately not persisted (like {@link #resetPending}):
     * a stale value after a reload just means a length change can't retroactively shorten a pulse
     * that predates the reload, which self-corrects the moment that pulse ends on its own.
     */
    private long pulseStartGameTime = -1;
    /**
     * Whether the pulse currently running (if any) was started by an external driver (a bound
     * advanced sensor calling {@link CardReaderBlock#acceptPulse} rather than a direct tap) - set
     * once at the rising edge, alongside {@link #pulseStartGameTime}, and read by
     * {@link CardReaderBlock#tickPulseTimeout} to decide what "the hold just ended" means: for a
     * driver-started pulse, release the instant the driver stops wanting it held (see
     * {@link #isExternallyHeld}), same as before; for a directly-tapped one, fall back to this
     * reader's own configured length as always, even if some other bound sensor also happened to
     * extend it along the way.
     */
    private boolean pulseExternallyOriginated;
    /**
     * Game time until which an external driver (a bound advanced sensor, see
     * {@code AdvancedSensorBlockEntity}) wants this reader's accept pulse held open, regardless
     * of this reader's own {@link #pulseStartGameTime}/{@link #getSignalLength} timing.
     * {@code -1} while nobody's holding it. {@link CardReaderBlock#tickPulseTimeout} is still the
     * only place that ever calls {@link CardReaderBlock#releasePulse} - a driver only ever
     * pushes this deadline forward (see {@link #holdExternalSignal}), it never releases the
     * pulse itself, so a driver's own hold-delay lapsing can no longer race against this reader's
     * own timeout to flip the pulse off and immediately back on again.
     */
    private long externalHoldUntilGameTime = -1;

    private final LinkDeviceState linkState = new LinkDeviceState(this);

    public CardReaderBlockEntity(BlockPos pos, BlockState state) {
        super(DKBlockEntities.CARD_READER.get(), pos, state);
    }

    public boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    /**
     * {@code null} leaves the reader <em>neutral</em>: no player, and no estate card, can match a
     * missing owner, so only the golden cards can administer it - see {@link MaintenanceAccess}
     * and {@code /dynamickeycards release}.
     */
    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        this.syncToClient();
    }

    public boolean isRegisterMode() {
        return registerMode;
    }

    public void setRegisterMode(boolean registerMode) {
        this.registerMode = registerMode;
        this.clearPendingActions();
        this.syncToClient();
    }

    /**
     * Whether a golden-keycard full reset is awaiting its confirming second click - same
     * confirm timer the wrench pickup uses, see {@link WrenchPickupState}.
     */
    public boolean isResetPending() {
        return resetPending.isPending(level);
    }

    /** Arms the confirmation - a second click while it is still armed confirms it. */
    public void armResetPending() {
        resetPending.arm(level);
    }

    /** Same shape as {@link #isResetPending()}, for the sneak-wrench pickup confirmation. */
    @Override
    public boolean isWrenchPickupPending() {
        return wrenchPickup.isPending(level);
    }

    /** Same shape as {@link #armResetPending()}, for the sneak-wrench pickup confirmation. */
    @Override
    public void armWrenchPickupPending() {
        wrenchPickup.arm(level);
    }

    /**
     * Cancels any armed confirmation outright - same end state as letting it time out, but
     * immediate. Called whenever the player does something else with this reader that isn't
     * the confirming click itself (register-mode change, opening the wrench config UI), on
     * the theory that a destructive confirmation should only ever fire right after its own warning.
     */
    @Override
    public void clearPendingActions() {
        resetPending.clear();
        wrenchPickup.clear();
    }


    /** The accept-pulse length in ticks: this reader's override, else the config default. */
    @Override
    public int getSignalLength() {
        return signalLength >= 0 ? signalLength : DKConfig.DEFAULT_PULSE_LENGTH_TICKS.get();
    }

    public boolean hasSignalLengthOverride() {
        return signalLength >= 0;
    }

    @Override
    public void setSignalLength(int ticks) {
        this.signalLength = ticks;
        this.syncToClient();
    }

    /** Back to the config default. */
    @Override
    public void clearSignalLength() {
        this.signalLength = -1;
        this.syncToClient();
    }

    /** Called by {@link CardReaderBlock#acceptPulse} the moment a pulse begins. */
    void onPulseStarted(boolean externallyOriginated) {
        if (level != null) {
            pulseStartGameTime = level.getGameTime();
        }
        this.pulseExternallyOriginated = externallyOriginated;
    }

    /** Game time {@link #onPulseStarted}'s pulse began; {@code -1} if no pulse has run yet. */
    long getPulseStartGameTime() {
        return pulseStartGameTime;
    }

    /** See {@link #pulseExternallyOriginated}. */
    boolean isPulseExternallyOriginated() {
        return pulseExternallyOriginated;
    }

    /**
     * Called every tick by an external driver (a bound advanced sensor) that currently wants
     * this reader's accept pulse held open. Only ever pushes the deadline forward
     * ({@code Math.max}), so multiple simultaneous drivers - or this call racing against
     * {@link CardReaderBlock#tickPulseTimeout} in either order - can't undo each other.
     */
    void holdExternalSignal(long untilGameTime) {
        this.externalHoldUntilGameTime = Math.max(this.externalHoldUntilGameTime, untilGameTime);
    }

    /** Whether an external driver still wants this reader's pulse held open right now - see {@link #holdExternalSignal}. */
    boolean isExternallyHeld(long now) {
        return now < externalHoldUntilGameTime;
    }

    /** Ghost frequency slot {@code index} (0 or 1) for Create's Redstone Link broadcast. */
    @Override
    public ItemStack getFrequencySlot(int index) {
        return linkState.getFrequencySlot(index);
    }

    /**
     * Sets ghost frequency slot {@code index}; the stack is never actually consumed by this
     * (see {@code menu.LinkDeviceMenu}), only remembered as a count-1 copy — stack count
     * doesn't matter for frequency matching.
     */
    @Override
    public void setFrequencySlot(int index, ItemStack stack) {
        linkState.setFrequencySlot(index, stack);
        this.syncToClient();
    }

    @Override
    public SignalMode getSignalMode() {
        return linkState.getSignalMode();
    }

    /** Whether the physical redstone wire should carry the accept pulse right now. */
    public boolean isPhysicalSignalActive() {
        return linkState.getSignalMode().physicalActive;
    }

    /** Set by the wrench UI's normal/link/mixed mode buttons. */
    @Override
    public void setSignalMode(SignalMode signalMode) {
        linkState.setSignalMode(signalMode);
        this.syncToClient();
    }

    @Override
    public int getLinkStrength() {
        return getBlockState().getValue(CardReaderBlock.MODE) == CardReaderMode.ACCEPTED ? 15 : 0;
    }

    /**
     * Tells Create's Redstone Link network to re-poll this reader's transmitted strength.
     * Called by {@link CardReaderBlock} right after the accept pulse starts and right after it
     * ends, so the link strength (15 while accepted, else 0) tracks the accept pulse.
     * A no-op unless Create is installed and this reader is currently registered.
     */
    public void notifyLinkChanged() {
        linkState.notifyLinkChanged();
    }

    @Override
    public UUID getDeviceId() {
        return deviceId;
    }

    /**
     * While a {@link BoundReaderMode#SENSOR_CENTRIC_SIMULTANEOUS}-bound sensor is actively driving
     * this reader (see {@link #publishSensorRawSignal}), mirrors that sensor's own raw detection
     * exactly - the same value a receiver bound directly to that sensor would see, so the two are
     * indistinguishable in that mode. While a {@link BoundReaderMode#SIMULTANEOUS}-bound sensor is
     * driving it instead (see {@link #keepSimultaneousBlipModeActive}), reports
     * {@link #MOMENTARY_TICKS}' short blip per independent re-trigger rather than this reader's
     * full held-open MODE, since that mode's re-triggers are a series of distinct events (see
     * {@link #MOMENTARY_TICKS}'s own doc for why). Otherwise (a direct tap, or a
     * {@link BoundReaderMode#READER_ONLY}-bound sensor) mirrors this reader's own physical accept
     * state ({@link CardReaderBlock#MODE}) - the same value {@link #getLinkStrength} already
     * reports for Create's Redstone Link - so a receiver bound to this reader stays high for
     * exactly as long as the reader's own accept pulse is physically held open (a direct tap's
     * {@link #getSignalLength}, or an externally-held one's actual hold duration).
     */
    @Override
    public int getSignalSourceStrength() {
        if (level == null) {
            return 0;
        }
        long now = level.getGameTime();
        if (now < sensorRawOverrideUntilGameTime) {
            return sensorRawOverrideStrength;
        }
        if (now < simultaneousBlipActiveUntilGameTime) {
            return now < momentaryUntilGameTime ? 15 : 0;
        }
        return getBlockState().getValue(CardReaderBlock.MODE) == CardReaderMode.ACCEPTED ? 15 : 0;
    }

    /**
     * Called every tick by a {@link BoundReaderMode#SENSOR_CENTRIC_SIMULTANEOUS}-bound advanced
     * sensor with its own current raw detection - makes {@link #getSignalSourceStrength} mirror
     * that value exactly (bypassing this reader's own MODE-based timing) for
     * {@link #SENSOR_OVERRIDE_BUFFER_TICKS}, refreshed every tick the sensor keeps calling this -
     * see {@link #sensorRawOverrideUntilGameTime} for why that's a self-expiring window rather
     * than an explicit on/off toggle.
     */
    void publishSensorRawSignal(Level level, boolean raw) {
        if (level == null) {
            return;
        }
        sensorRawOverrideUntilGameTime = level.getGameTime() + SENSOR_OVERRIDE_BUFFER_TICKS;
        int strength = raw ? 15 : 0;
        if (strength != sensorRawOverrideStrength) {
            sensorRawOverrideStrength = strength;
            publishSignalSource(level);
        }
    }

    /**
     * Called every tick by a {@link BoundReaderMode#SIMULTANEOUS}-bound advanced sensor (whether or
     * not it's actively re-triggering this reader this exact tick) to keep
     * {@link #getSignalSourceStrength} on {@link #momentaryUntilGameTime}'s short-blip window
     * instead of this reader's own MODE - see {@link #simultaneousBlipActiveUntilGameTime}'s own
     * doc for why this needs its own continuous refresh separate from
     * {@link #markSignalSourceTriggered}'s (which only fires at each individual re-trigger).
     */
    void keepSimultaneousBlipModeActive(Level level) {
        if (level != null) {
            simultaneousBlipActiveUntilGameTime = level.getGameTime() + SENSOR_OVERRIDE_BUFFER_TICKS;
        }
    }

    /**
     * Called by {@link CardReaderBlock#acceptPulse} each time a
     * {@link BoundReaderMode#SIMULTANEOUS}-bound sensor independently re-triggers this reader -
     * starts (or restarts) the short {@link #MOMENTARY_TICKS} window
     * {@link #getSignalSourceStrength} reports 15 for, independent of this reader's own
     * {@link #getSignalLength} pulse duration. A no-op (and harmless) if called while
     * {@link #simultaneousBlipActiveUntilGameTime} isn't active - {@link #getSignalSourceStrength}
     * simply won't consult {@link #momentaryUntilGameTime} in that case.
     */
    void markSignalSourceTriggered() {
        if (level != null) {
            momentaryUntilGameTime = level.getGameTime() + MOMENTARY_TICKS;
        }
        publishSignalSource(level);
    }

    /**
     * Called every tick (see {@link CardReaderBlock#getTicker}) to drop the wireless broadcast
     * back to 0 once {@link #MOMENTARY_TICKS} has passed, for a
     * {@link BoundReaderMode#SIMULTANEOUS}-bound sensor's blip - independent of, and much shorter
     * than, {@link CardReaderBlock#tickPulseTimeout}'s own release timing. A no-op whenever
     * {@link #momentaryUntilGameTime} isn't running, i.e. every tick this reader isn't currently
     * driven by a SIMULTANEOUS-bound sensor's re-trigger.
     */
    void tickSignalSourcePublish(Level level) {
        if (momentaryUntilGameTime >= 0 && level.getGameTime() >= momentaryUntilGameTime) {
            momentaryUntilGameTime = -1;
            publishSignalSource(level);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        linkState.onLoad();
        DeviceIndex index = DeviceRegistry.registerOnLoad(this, deviceId);
        if (index != null) {
            publishSignalSource(level);
            resolveLegacyLinkedReaders(index);
        }
    }

    /**
     * Best-effort migration for a pre-0.1.8 save's {@code BlockPos}-based links (see
     * {@link #legacyLinkedReaderPositions}): resolves each stored position to the reader actually
     * there right now and adopts its {@link #deviceId}. Only works for a target whose chunk
     * happens to be loaded at this exact moment - one whose chunk is still unloaded is simply
     * dropped rather than force-loaded (out of scope for this pass, see {@code ROADMAP.md}), so a
     * link to a very distant reader may need to be re-established by hand after upgrading. Runs
     * once - {@link #legacyLinkedReaderPositions} is cleared after, regardless of how many
     * resolved, so a reload doesn't keep re-attempting positions that were already given up on.
     */
    private void resolveLegacyLinkedReaders(DeviceIndex index) {
        if (legacyLinkedReaderPositions.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (BlockPos pos : legacyLinkedReaderPositions) {
            if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                    && level.getBlockEntity(pos) instanceof CardReaderBlockEntity linked) {
                linkedReaderIds.add(linked.deviceId);
                changed = true;
            }
        }
        legacyLinkedReaderPositions = List.of();
        if (changed) {
            this.syncToClient();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        linkState.setRemoved();
    }

    public boolean isRegistered(UUID cardId) {
        return registeredCards.contains(cardId);
    }

    public void registerCard(UUID cardId) {
        registeredCards.add(cardId);
        this.syncToClient();
    }

    /** True if any of the given keys is registered. Blocking is checked separately, first. */
    public boolean isRegisteredAny(Iterable<UUID> keys) {
        for (UUID key : keys) {
            if (registeredCards.contains(key)) {
                return true;
            }
        }
        return false;
    }

    public int getRegisteredCount() {
        return registeredCards.size();
    }

    public void removeCard(UUID cardId) {
        registeredCards.remove(cardId);
        this.syncToClient();
    }

    public void clearCards() {
        registeredCards.clear();
        blockedCards.clear();
        this.syncToClient();
    }

    public Set<UUID> getLinkedReaders() {
        return Set.copyOf(linkedReaderIds);
    }

    /** Server-side only; doesn't touch the other end - see {@code CardReaderBlock#setPlacedBy} for the mutual case. */
    public void addLinkedReader(UUID id) {
        if (linkedReaderIds.add(id)) {
            this.syncToClient();
        }
    }

    /**
     * Server-side only; doesn't touch the other end - see {@code CardReaderBlock#onRemove}, which
     * calls this on every reader still pointing at one that's being destroyed, so a stale entry
     * can never linger. Unlike the old {@link BlockPos}-based version, a stale id can't be
     * silently, one-sidedly inherited by an unrelated reader either way - a fresh reader always
     * gets a fresh {@link #deviceId} - but cleaning it up still keeps {@link #getLinkedReaders}
     * accurate for anything that reads it directly (e.g. a future UI).
     */
    public void removeLinkedReader(UUID id) {
        if (linkedReaderIds.remove(id)) {
            this.syncToClient();
        }
    }

    /**
     * This reader plus every reader reachable by following {@link #linkedReaderIds} from here - a
     * breadth-first walk of the whole connected group, however many readers are in it or whatever
     * shape they're linked in (chain, star, even a loop; a loop is exactly as safe as any other
     * shape since {@code visited} stops it from being walked twice). Two readers linked only
     * through a third one still end up sharing one accept list this way, which a single
     * direct-neighbor check wouldn't give them.
     *
     * <p>Resolving an id to a position (via {@link DeviceIndex}) and then reading its actual data
     * still needs {@code Level#getBlockEntity}, which force-loads whatever chunk that position is
     * in if it isn't already loaded (confirmed against vanilla's own {@code LevelReader}) - the id
     * migration fixes staleness and Create-move safety, not this cost, see the class doc. Callers
     * on a hot path (see {@link #accepts(ItemStack)}/{@link #acceptsAny}) should call this once
     * and reuse the result rather than once per item/check.
     */
    private Set<CardReaderBlockEntity> linkedGroup() {
        Set<CardReaderBlockEntity> visited = new HashSet<>();
        visited.add(this);
        if (!(level instanceof ServerLevel serverLevel)) {
            return visited;
        }
        DeviceIndex index = DeviceIndex.get(serverLevel);
        Deque<CardReaderBlockEntity> frontier = new ArrayDeque<>(visited);
        while (!frontier.isEmpty()) {
            CardReaderBlockEntity current = frontier.poll();
            for (UUID id : current.linkedReaderIds) {
                BlockPos pos = index.getPosition(id);
                if (pos != null && level.getBlockEntity(pos) instanceof CardReaderBlockEntity linked && visited.add(linked)) {
                    frontier.add(linked);
                }
            }
        }
        return visited;
    }

    public boolean isBlocked(UUID ownKey) {
        return blockedCards.contains(ownKey);
    }

    public void blockCard(UUID ownKey) {
        blockedCards.add(ownKey);
        this.syncToClient();
    }

    public void unblockCard(UUID ownKey) {
        blockedCards.remove(ownKey);
        this.syncToClient();
    }

    /**
     * Whether {@code key} is registered anywhere in this reader's linked group (see
     * {@link #linkedGroup()}), not just here - what {@link CardReaderBlock#useItemOn}'s
     * register-mode tap should check before deciding to register vs. remove, so tapping a card
     * that's already registered via a linked reader is recognized as "already has access" instead
     * of silently registering a second, redundant local copy here too.
     */
    public boolean isRegisteredInGroup(UUID key) {
        return isRegisteredAnyIn(List.of(key), linkedGroup());
    }

    /** Same as {@link #isRegisteredInGroup}, for {@link #isBlocked}. */
    public boolean isBlockedInGroup(UUID key) {
        return isBlockedIn(key, linkedGroup());
    }

    /** Same as {@link #isRegisteredInGroup}, for {@link #isRegisteredAny}. */
    public boolean isRegisteredAnyInGroup(Iterable<UUID> keys) {
        return isRegisteredAnyIn(keys, linkedGroup());
    }

    /**
     * Removes {@code key} from wherever it's actually stored across this reader's linked group -
     * not just here. {@link #removeCard} alone would leave a stale registration on whichever
     * reader the card was originally registered on, so revoking from a different reader in the
     * same group would silently not take.
     */
    public void removeRegisteredFromGroup(UUID key) {
        for (CardReaderBlockEntity reader : linkedGroup()) {
            reader.removeCard(key);
        }
    }

    /** Same as {@link #removeRegisteredFromGroup}, for {@link #unblockCard}. */
    public void unblockInGroup(UUID key) {
        for (CardReaderBlockEntity reader : linkedGroup()) {
            reader.unblockCard(key);
        }
    }

    /**
     * Whether tapping {@code stack} against this reader right now would be accepted - the same
     * rule {@link CardReaderBlock#useItemOn}'s tap path applies, minus the side effects. Register
     * mode isn't considered here - that's a physical-tap-only flow (arming/toggling
     * registration), not something an ambient sensor should ever trigger.
     */
    public boolean accepts(ItemStack stack) {
        return accepts(stack, linkedGroup());
    }

    /**
     * Whether any non-empty stack in {@code inventory} would be accepted by this reader right
     * now - same rule as {@link #accepts(ItemStack)}, but for {@code AdvancedSensorBlockEntity}'s
     * per-tick whole-inventory scan: {@link #linkedGroup()} (a BFS that can force-load every
     * linked reader's chunk, see its own doc) is computed once for the whole inventory here,
     * instead of once per stack the way calling {@link #accepts(ItemStack)} in a loop would.
     */
    public boolean acceptsAny(Inventory inventory) {
        Set<CardReaderBlockEntity> group = linkedGroup();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && accepts(stack, group)) {
                return true;
            }
        }
        return false;
    }

    private boolean accepts(ItemStack stack, Set<CardReaderBlockEntity> group) {
        if (stack.getItem() instanceof GoldenKeycardItem) {
            return true;
        }
        if (stack.getItem() instanceof EstateKeycardItem) {
            UUID cardOwner = EstateKeycardItem.boundOwner(stack);
            return cardOwner != null && cardOwner.equals(owner);
        }
        if (!(stack.getItem() instanceof KeycardItem)) {
            return false;
        }
        UUID ownKey = KeycardItem.ownKey(stack);
        if (ownKey == null || isBlockedIn(ownKey, group)) {
            return false;
        }
        return isRegisteredAnyIn(KeycardItem.allKeys(stack), group);
    }

    /** {@link #isBlocked}, but honoring every reader in {@code group} - see {@link #linkedGroup()}. */
    private static boolean isBlockedIn(UUID ownKey, Set<CardReaderBlockEntity> group) {
        for (CardReaderBlockEntity reader : group) {
            if (reader.isBlocked(ownKey)) {
                return true;
            }
        }
        return false;
    }

    /** {@link #isRegisteredAny}, but honoring every reader in {@code group} - see {@link #linkedGroup()}. */
    private static boolean isRegisteredAnyIn(Iterable<UUID> keys, Set<CardReaderBlockEntity> group) {
        for (CardReaderBlockEntity reader : group) {
            if (reader.isRegisteredAny(keys)) {
                return true;
            }
        }
        return false;
    }

    private void syncToClient() {
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        DeviceRegistry.save(tag, deviceId);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        ListTag cards = new ListTag();
        for (UUID id : registeredCards) {
            cards.add(NbtUtils.createUUID(id));
        }
        tag.put("Cards", cards);
        ListTag blocked = new ListTag();
        for (UUID id : blockedCards) {
            blocked.add(NbtUtils.createUUID(id));
        }
        tag.put("Blocked", blocked);
        tag.putBoolean("RegisterMode", registerMode);
        if (signalLength >= 0) {
            tag.putInt("PulseLength", signalLength);
        }
        linkState.save(tag, registries);
        ListTag linked = new ListTag();
        for (UUID id : linkedReaderIds) {
            linked.add(NbtUtils.createUUID(id));
        }
        tag.put("LinkedReaderIds", linked);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        deviceId = DeviceRegistry.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        registeredCards.clear();
        for (Tag card : tag.getList("Cards", Tag.TAG_INT_ARRAY)) {
            registeredCards.add(NbtUtils.loadUUID(card));
        }
        blockedCards.clear();
        for (Tag card : tag.getList("Blocked", Tag.TAG_INT_ARRAY)) {
            blockedCards.add(NbtUtils.loadUUID(card));
        }
        registerMode = tag.getBoolean("RegisterMode");
        signalLength = tag.contains("PulseLength") ? tag.getInt("PulseLength") : -1;
        linkState.load(tag, registries);
        // pre-0.1.3 saves only had the boolean normal/broadcast toggle, which behaved exactly
        // like today's SIMULTANEOUS (physical pulse always went out, broadcast just added the link on
        // top) - migrate straight to that so old worlds don't change behavior underfoot. Newer
        // saves already have an explicit SignalMode entry, which linkState.load just applied.
        if (!tag.contains("SignalMode") && tag.getBoolean("BroadcastEnabled")) {
            linkState.setSignalModeRaw(SignalMode.SIMULTANEOUS);
        }
        linkedReaderIds.clear();
        if (tag.contains("LinkedReaderIds")) {
            // 0.1.8+ save: already device ids, no resolution needed
            for (Tag entry : tag.getList("LinkedReaderIds", Tag.TAG_INT_ARRAY)) {
                linkedReaderIds.add(NbtUtils.loadUUID(entry));
            }
        } else {
            // pre-0.1.8 save: BlockPos-based, resolved to ids best-effort once the level is
            // available - see #onLoad/#resolveLegacyLinkedReaders
            List<BlockPos> legacy = new ArrayList<>();
            // pre-0.1.6 saves only ever had one entry, under the old singular key
            if (tag.contains("LinkedReader")) {
                NbtUtils.readBlockPos(tag, "LinkedReader").ifPresent(legacy::add);
            }
            for (Tag entry : tag.getList("LinkedReaders", Tag.TAG_INT_ARRAY)) {
                if (entry instanceof IntArrayTag intArray && intArray.getAsIntArray().length == 3) {
                    int[] xyz = intArray.getAsIntArray();
                    legacy.add(new BlockPos(xyz[0], xyz[1], xyz[2]));
                }
            }
            legacyLinkedReaderPositions = legacy;
        }
        // only ever non-null here (as opposed to during a plain disk-chunk load, where the level
        // isn't attached until after this returns) when NBT is being pasted onto an already-placed
        // block - a Create schematic print, most notably. Re-registers unconditionally (not just on
        // a confirmed collision) since onLoad may already have run earlier in that same sequence,
        // before this deviceId was known, registering a since-discarded temporary one instead.
        if (level instanceof ServerLevel serverLevel) {
            // linkedReaderIds is left untouched on a supersede - a linked reader that was part of
            // the same print resolves to its own printed sibling once that one resolves its id,
            // and one that wasn't keeps resolving to the same real reader; see
            // DeviceRegistry#registerResolvingDuplicate
            deviceId = DeviceRegistry.registerResolvingDuplicate(
                    this, serverLevel, DeviceIndex.get(serverLevel), deviceId, null);
            publishSignalSource(level);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
