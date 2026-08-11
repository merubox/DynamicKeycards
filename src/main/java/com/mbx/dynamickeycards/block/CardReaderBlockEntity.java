package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DKConfig;
import com.mbx.dynamickeycards.compat.create.CreateLinkCompat;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * State for a card reader: the owner it bound to when placed, the set of registered
 * keycard ids, and whether register mode is active. Fully synced to clients (update tag +
 * data packet) so both sides resolve interactions the same way. The {@link LinkDeviceBlockEntity}
 * half (signal mode, frequency slots, signal length) is shared with the motion sensors - see
 * that interface for what's generic here versus reader-specific.
 */
public class CardReaderBlockEntity extends BlockEntity implements LinkDeviceBlockEntity {

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
     * a tap here accepts.
     *
     * <p>A reader can be directly linked to more than one other reader - chaining three or more
     * readers this way (A-B, then B-C) used to silently break A-B, back when this was a single
     * nullable position that the second link simply overwrote. The whole connected group -
     * however many readers, in whatever shape (chain, star, even a loop) - is meant to share one
     * combined accept list, so {@link #accepts} walks the full reachable set (see
     * {@link #linkedGroup()}) rather than just this field's own direct entries.
     */
    private final Set<BlockPos> linkedReaderPositions = new HashSet<>();
    private boolean registerMode;
    /**
     * How long a destructive action's confirming second click stays armed before it has to be
     * re-started from scratch.
     */
    private static final int PENDING_CONFIRM_TICKS = 60;
    /**
     * Game time {@link #armResetPending()} last ran; {@code -1} while no golden-keycard full
     * reset is awaiting its confirming second click. Not persisted (like {@link #pulseStartGameTime}
     * below) - a stale confirmation across a reload safely reads as expired.
     */
    private long resetPendingStartTime = -1;
    /** Same shape as {@link #resetPendingStartTime}, for the sneak-wrench pickup confirmation. */
    private long wrenchPickupPendingStartTime = -1;
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
     * {@code -1} while no pulse is running. Deliberately not persisted (like {@link #resetPendingStartTime}):
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

    /**
     * Create Redstone Link (only meaningful when Create is installed): the two ghost frequency
     * slots, and the opaque {@link CreateLinkCompat} adapter registered against Create's network
     * while this reader is loaded and {@link #signalMode} calls for it. Both stay empty/
     * {@code null} without Create.
     */
    private final ItemStack[] frequencySlots = {ItemStack.EMPTY, ItemStack.EMPTY};
    /** Set from the wrench UI's three mode buttons. */
    private SignalMode signalMode = SignalMode.NORMAL;
    @Nullable
    private Object createLinkAdapter;

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

    public void setOwner(UUID owner) {
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
     * Whether a golden-keycard full reset is awaiting its confirming second click, within
     * {@link #PENDING_CONFIRM_TICKS} of when it was armed.
     */
    public boolean isResetPending() {
        return isPending(resetPendingStartTime);
    }

    /** Arms the confirmation - a second click within {@link #PENDING_CONFIRM_TICKS} confirms it. */
    public void armResetPending() {
        resetPendingStartTime = level != null ? level.getGameTime() : -1;
    }

    /** Same shape as {@link #isResetPending()}, for the sneak-wrench pickup confirmation. */
    @Override
    public boolean isWrenchPickupPending() {
        return isPending(wrenchPickupPendingStartTime);
    }

    /** Same shape as {@link #armResetPending()}, for the sneak-wrench pickup confirmation. */
    @Override
    public void armWrenchPickupPending() {
        wrenchPickupPendingStartTime = level != null ? level.getGameTime() : -1;
    }

    /**
     * Cancels any armed confirmation outright - same end state as letting it time out, but
     * immediate. Called whenever the player does something else with this reader that isn't
     * the confirming click itself (register-mode change, opening the wrench config UI), on
     * the theory that a destructive confirmation should only ever fire right after its own warning.
     */
    @Override
    public void clearPendingActions() {
        resetPendingStartTime = -1;
        wrenchPickupPendingStartTime = -1;
    }

    private boolean isPending(long startTime) {
        return startTime >= 0 && level != null && level.getGameTime() - startTime < PENDING_CONFIRM_TICKS;
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
        return frequencySlots[index];
    }

    /**
     * Sets ghost frequency slot {@code index}; the stack is never actually consumed by this
     * (see {@code menu.LinkDeviceMenu}), only remembered as a count-1 copy — stack count
     * doesn't matter for frequency matching.
     */
    @Override
    public void setFrequencySlot(int index, ItemStack stack) {
        frequencySlots[index] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        reregisterLink();
        this.syncToClient();
    }

    @Override
    public SignalMode getSignalMode() {
        return signalMode;
    }

    /** Whether the physical redstone wire should carry the accept pulse right now. */
    public boolean isPhysicalSignalActive() {
        return signalMode.physicalActive;
    }

    /** Set by the wrench UI's normal/link/mixed mode buttons. */
    @Override
    public void setSignalMode(SignalMode signalMode) {
        this.signalMode = signalMode;
        if (signalMode.linkActive) {
            registerLink();
        } else {
            unregisterLink();
        }
        this.syncToClient();
    }

    @Override
    public int getLinkStrength() {
        return getBlockState().getValue(CardReaderBlock.MODE) == CardReaderMode.ACCEPTED ? 15 : 0;
    }

    /** Re-announces this reader to Create's Redstone Link network under its current frequency. */
    private void reregisterLink() {
        if (createLinkAdapter != null && level != null) {
            CreateLinkCompat.unregister(level, createLinkAdapter);
            CreateLinkCompat.register(level, createLinkAdapter);
        }
    }

    private void registerLink() {
        if (level == null || level.isClientSide || !CreateLinkCompat.isLoaded()) {
            return;
        }
        if (createLinkAdapter == null) {
            createLinkAdapter = CreateLinkCompat.createAdapter(this);
        }
        CreateLinkCompat.register(level, createLinkAdapter);
    }

    private void unregisterLink() {
        if (createLinkAdapter != null && level != null) {
            CreateLinkCompat.unregister(level, createLinkAdapter);
        }
    }

    /**
     * Tells Create's Redstone Link network to re-poll this reader's transmitted strength.
     * Called by {@link CardReaderBlock} right after the accept pulse starts and right after it
     * ends, so the link strength (15 while accepted, else 0) tracks the accept pulse.
     * A no-op unless Create is installed and this reader is currently registered.
     */
    public void notifyLinkChanged() {
        if (createLinkAdapter != null && level != null) {
            CreateLinkCompat.notifyChanged(level, createLinkAdapter);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (signalMode.linkActive) {
            registerLink();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        unregisterLink();
        createLinkAdapter = null;
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

    public Set<BlockPos> getLinkedReaders() {
        return Set.copyOf(linkedReaderPositions);
    }

    /** Server-side only; doesn't touch the other end - see {@code CardReaderBlock#setPlacedBy} for the mutual case. */
    public void addLinkedReader(BlockPos pos) {
        if (linkedReaderPositions.add(pos)) {
            this.syncToClient();
        }
    }

    /**
     * Server-side only; doesn't touch the other end - see {@code CardReaderBlock#onRemove}, which
     * calls this on every reader still pointing at one that's being destroyed, so a stale entry
     * can never linger and later get silently, one-sidedly inherited by whatever unrelated reader
     * a player happens to place at that same position afterward.
     */
    public void removeLinkedReader(BlockPos pos) {
        if (linkedReaderPositions.remove(pos)) {
            this.syncToClient();
        }
    }

    /**
     * Rewrites every entry in {@link #linkedReaderPositions} through {@code transform} - called
     * from {@code compat.create.PositionTransformCompat} when this reader is moved as a whole (a
     * Create schematic printed at an offset/rotation, or a contraption disassembling elsewhere).
     * A stored position is a world-absolute {@link BlockPos}, so without this it would keep
     * pointing at wherever a linked reader used to be, not wherever this move actually put it.
     * Applied unconditionally rather than checking whether a reader now exists there: if a linked
     * reader moved along with this one (the common case - they're usually part of the same
     * build), the transformed position is exactly right; if it didn't move with this one, the
     * position was already going to be wrong either way, and this doesn't make that any worse -
     * {@link #linkedGroup()} already treats "nothing of the right type there" as simply
     * unreachable.
     */
    public void applyPositionTransform(UnaryOperator<BlockPos> transform) {
        if (linkedReaderPositions.isEmpty()) {
            return;
        }
        Set<BlockPos> transformed = new HashSet<>();
        for (BlockPos pos : linkedReaderPositions) {
            transformed.add(transform.apply(pos));
        }
        linkedReaderPositions.clear();
        linkedReaderPositions.addAll(transformed);
        this.syncToClient();
    }

    /**
     * This reader plus every reader reachable by following {@link #linkedReaderPositions} from
     * here - a breadth-first walk of the whole connected group, however many readers are in it or
     * whatever shape they're linked in (chain, star, even a loop; a loop is exactly as safe as
     * any other shape since {@code visited} stops it from being walked twice). Two readers linked
     * only through a third one still end up sharing one accept list this way, which a single
     * direct-neighbor check wouldn't give them.
     */
    private Set<CardReaderBlockEntity> linkedGroup() {
        Set<CardReaderBlockEntity> visited = new HashSet<>();
        visited.add(this);
        if (level == null) {
            return visited;
        }
        Deque<CardReaderBlockEntity> frontier = new ArrayDeque<>(visited);
        while (!frontier.isEmpty()) {
            CardReaderBlockEntity current = frontier.poll();
            for (BlockPos pos : current.linkedReaderPositions) {
                if (level.getBlockEntity(pos) instanceof CardReaderBlockEntity linked && visited.add(linked)) {
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
     * Whether tapping {@code stack} against this reader right now would be accepted - the same
     * rule {@link CardReaderBlock#useItemOn}'s tap path applies, minus the side effects, so
     * {@code AdvancedSensorBlockEntity} can check a player's whole inventory for a valid card
     * without actually needing them to tap it. Register mode isn't considered here - that's a
     * physical-tap-only flow (arming/toggling registration), not something an ambient sensor
     * should ever trigger.
     */
    public boolean accepts(ItemStack stack) {
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
        if (ownKey == null || isBlockedHere(ownKey)) {
            return false;
        }
        return isRegisteredAnyHere(KeycardItem.allKeys(stack));
    }

    /** {@link #isBlocked}, but honoring the whole linked group's block lists - see {@link #linkedGroup()}. */
    private boolean isBlockedHere(UUID ownKey) {
        for (CardReaderBlockEntity reader : linkedGroup()) {
            if (reader.isBlocked(ownKey)) {
                return true;
            }
        }
        return false;
    }

    /** {@link #isRegisteredAny}, but honoring the whole linked group's registrations - see {@link #linkedGroup()}. */
    private boolean isRegisteredAnyHere(Iterable<UUID> keys) {
        for (CardReaderBlockEntity reader : linkedGroup()) {
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
        if (!frequencySlots[0].isEmpty()) {
            tag.put("FrequencySlot0", frequencySlots[0].save(registries));
        }
        if (!frequencySlots[1].isEmpty()) {
            tag.put("FrequencySlot1", frequencySlots[1].save(registries));
        }
        tag.putString("SignalMode", signalMode.name());
        ListTag linked = new ListTag();
        for (BlockPos pos : linkedReaderPositions) {
            linked.add(NbtUtils.writeBlockPos(pos));
        }
        tag.put("LinkedReaders", linked);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
        frequencySlots[0] = tag.contains("FrequencySlot0")
                ? ItemStack.parseOptional(registries, tag.getCompound("FrequencySlot0")) : ItemStack.EMPTY;
        frequencySlots[1] = tag.contains("FrequencySlot1")
                ? ItemStack.parseOptional(registries, tag.getCompound("FrequencySlot1")) : ItemStack.EMPTY;
        // pre-0.1.3 saves only had the boolean normal/broadcast toggle, which behaved exactly
        // like today's MIXED (physical pulse always went out, broadcast just added the link on
        // top) - migrate straight to that so old worlds don't change behavior underfoot
        signalMode = tag.contains("SignalMode")
                ? SignalMode.byName(tag.getString("SignalMode"))
                : (tag.getBoolean("BroadcastEnabled") ? SignalMode.MIXED : SignalMode.NORMAL);
        linkedReaderPositions.clear();
        // pre-0.1.6 saves only ever had one entry, under the old singular key
        if (tag.contains("LinkedReader")) {
            NbtUtils.readBlockPos(tag, "LinkedReader").ifPresent(linkedReaderPositions::add);
        }
        for (Tag entry : tag.getList("LinkedReaders", Tag.TAG_INT_ARRAY)) {
            if (entry instanceof IntArrayTag intArray && intArray.getAsIntArray().length == 3) {
                int[] xyz = intArray.getAsIntArray();
                linkedReaderPositions.add(new BlockPos(xyz[0], xyz[1], xyz[2]));
            }
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
