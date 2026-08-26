package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.registry.DKBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * State for a motion sensor ({@link WallSensorBlock}/{@link CeilingSensorBlock}): the per-tick
 * entity scan (shared between both via {@link MotionSensorBlock}), and everything from
 * {@link LinkDeviceBlockEntity} - same signal mode / frequency slots / signal-length UI as the
 * card reader, minus anything keycard-related (no owner, no registered cards).
 *
 * <p>Unlike the reader's signal length (an accept-pulse duration), this device's signal length
 * is a release delay: how long to keep signalling after the last detected entity leaves. 0
 * means cut instantly; the button-row number box shows this the same way either way, see
 * {@code LinkDeviceScreen}.
 *
 * <p>{@link AdvancedSensorBlockEntity} extends this directly, reusing everything here as its
 * unbound behavior - see that class for what changes once it's bound to a reader.
 */
public class MotionSensorBlockEntity extends BlockEntity implements LinkDeviceBlockEntity {

    /** Same shape as {@link CardReaderBlockEntity#externalHoldUntilGameTime}. */

    /**
     * Stable identity in {@link DeviceIndex} - every sensor gets one (not just
     * {@link AdvancedSensorBlockEntity}), even though a plain sensor is never itself a
     * {@link SignalSource} and can no longer be bound to at all going forward (see
     * {@code MotionSensorBlock#tryBindItemInteraction}'s own doc) - kept for a plain sensor
     * that's still the target of a binding made before that rule existed, so it keeps resolving
     * exactly as it always did.
     */
    private UUID deviceId = UUID.randomUUID();

    private final WrenchPickupState wrenchPickup = new WrenchPickupState();
    /** Game time an entity was last detected; {@code -1} if none has been detected yet. */
    private long lastDetectedGameTime = -1;
    /**
     * Game time until which an external driver (a bound advanced sensor whose own detection
     * targets this one, see {@code AdvancedSensorBlockEntity}) wants this sensor's own signal
     * held on, regardless of this sensor's own local detection. {@code -1} while nobody's
     * holding it. This sensor's own {@link #tick} is still the only place that ever actually
     * flips {@code PRESENT} - a driver only ever pushes this deadline forward (see
     * {@link #holdExternalSignal}), never touches the blockstate directly - so two drivers (or a
     * driver and this sensor's own detection) can never race to independently flip the same
     * state twice in a row the way an early version of the reader-binding code did.
     */
    private long externalHoldUntilGameTime = -1;

    private final LinkDeviceState linkState = new LinkDeviceState(this);
    /** Release delay in ticks after the last detected entity leaves; 0 cuts the signal instantly. */
    private int signalLength = 10;

    /**
     * The confirmed detection zone, or {@code null} if this sensor's range has never been
     * edited - {@link #detectionZone} then falls back to {@link RangeBox#LEGACY_DEFAULT}, the
     * fixed column every sensor used before range-editing existed, so an existing world's
     * sensors keep detecting exactly what they always did until someone actually reconfigures
     * one. Only ever replaced wholesale, by {@link #confirmRangeEdit} - see that method for why
     * this is never mutated in place.
     */
    @Nullable
    private RangeBox zone;
    /**
     * Whether a range edit is currently armed - entered by right-clicking with redstone dust
     * (see {@code MotionSensorBlock#tryRangeEditInteraction}), confirmed by doing that again
     * (consuming one redstone dust and replacing {@link #zone} with {@link #editZone}), or
     * cancelled by any bare-hand click, same as {@code CardReaderBlockEntity}'s register mode.
     * Persisted alongside {@link #editZone} (see {@link #saveAdditional}) so a mid-edit sensor
     * keeps its in-progress box armed across a reload instead of silently losing it - the client
     * would otherwise have no way to learn an edit was in progress at all, since {@link #getUpdateTag}
     * reuses the same save data.
     */
    private boolean rangeEditMode;
    /**
     * The working copy being adjusted while {@link #rangeEditMode} is armed - starts as a copy
     * of {@link #detectionZone}'s current box (whichever of {@link #zone}/{@link RangeBox#LEGACY_DEFAULT}
     * applies) the moment edit mode is entered, and is what every {@code Ctrl+Scroll} adjustment
     * (see {@link #adjustRange}) actually changes. {@link #zone} itself - and therefore what the
     * sensor is really detecting - stays untouched until {@link #confirmRangeEdit} commits this
     * over it, so an in-progress edit can never affect live detection, and cancelling just
     * discards this copy without needing to undo anything.
     */
    @Nullable
    private RangeBox editZone;

    public MotionSensorBlockEntity(BlockPos pos, BlockState state) {
        this(DKBlockEntities.MOTION_SENSOR.get(), pos, state);
    }

    /** For {@code AdvancedSensorBlockEntity}, which is a {@code MotionSensorBlockEntity} registered under its own type. */
    protected MotionSensorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Spectators pass through walls and shouldn't trip a sensor meant for real presence. */
    private static boolean countsAsPresent(LivingEntity entity) {
        return !(entity instanceof Player player && player.isSpectator());
    }

    static void tick(Level level, BlockPos pos, BlockState state, MotionSensorBlockEntity be) {
        long now = level.getGameTime();
        boolean shouldSignal = computeShouldSignal(level, pos, state, be, now).withLinger() || be.isExternallyHeld(now);
        applyPresent(level, pos, state, be, shouldSignal);
    }

    /**
     * {@code raw}: detected this exact tick, nothing more - what a {@link SignalSource}-capable
     * sensor publishes to the wireless system (see {@code AdvancedSensorBlockEntity}), since a
     * device's own local release delay is only ever supposed to shape its own physical output,
     * never what gets broadcast (see {@link SignalSource#getSignalSourceStrength}'s own doc).
     * {@code withLinger}: {@code raw}, or still within the release-delay window after the last
     * detection - this sensor's own actual signal state, excluding any external hold (see
     * {@link #isExternallyHeld}), which is a separate, independent reason to signal.
     */
    record DetectionResult(boolean raw, boolean withLinger) {
    }

    /**
     * Package-visible so a bound advanced sensor driving another sensor
     * ({@code AdvancedSensorBlockEntity}) can compute the exact same thing for itself, both for
     * its own local output (using {@link DetectionResult#withLinger}) and for what to relay to
     * its target - the target ends up mirroring the driving sensor's own on/off pattern, release
     * delay included, rather than just a raw "detected right now" blip.
     */
    static DetectionResult computeShouldSignal(Level level, BlockPos pos, BlockState state, MotionSensorBlockEntity be, long now) {
        if (!(state.getBlock() instanceof MotionSensorBlock)) {
            return new DetectionResult(false, false);
        }
        boolean detected = !level.getEntitiesOfClass(LivingEntity.class, be.detectionZone(pos),
                MotionSensorBlockEntity::countsAsPresent).isEmpty();
        if (detected) {
            be.lastDetectedGameTime = now;
        }
        // 0t: no lingering, drops the instant nothing's detected. >0t: keeps signalling until
        // that many ticks have passed since the last detection, resetting if something re-enters.
        boolean withLinger = detected || (be.signalLength > 0 && be.lastDetectedGameTime >= 0
                && now - be.lastDetectedGameTime < be.signalLength);
        return new DetectionResult(detected, withLinger);
    }

    /**
     * This sensor's live detection zone: {@link #zone} once it's ever been configured, else
     * {@link RangeBox#LEGACY_DEFAULT} - never {@link #editZone}, which only ever feeds into
     * {@link #zone} via a successful {@link #confirmRangeEdit}. Shared by the plain zone scan
     * above and {@code AdvancedSensorBlockEntity#tickBoundToReader}'s card-carrying-player scan,
     * so both modes of detection always agree on what "in range" means for a given sensor.
     */
    public AABB detectionZone(BlockPos pos) {
        return (zone != null ? zone : RangeBox.LEGACY_DEFAULT).toAABB(pos);
    }

    public boolean isRangeEditMode() {
        return rangeEditMode;
    }

    /** The box currently being previewed/adjusted - only meaningful while {@link #isRangeEditMode()}. */
    @Nullable
    public RangeBox getEditZone() {
        return editZone;
    }

    /** Starts a fresh edit from whatever this sensor is currently detecting - see {@link #detectionZone}. */
    public void enterRangeEdit() {
        this.rangeEditMode = true;
        this.editZone = zone != null ? zone : RangeBox.LEGACY_DEFAULT;
        this.syncToClient();
    }

    /** Discards {@link #editZone} without touching {@link #zone} - live detection never changed to begin with. */
    public void cancelRangeEdit() {
        this.rangeEditMode = false;
        this.editZone = null;
        this.syncToClient();
    }

    /**
     * Commits {@link #editZone} over {@link #zone}, ending the edit, and reports whether that
     * actually changed anything - a confirm that leaves the detection zone exactly as it already
     * was shouldn't cost the caller a redstone dust (see {@code MotionSensorBlock#tryConfirmRangeEdit}).
     * Always replaces the whole box rather than patching it in place - {@link #editZone} was
     * already clamped to the legal envelope on every single adjustment (see {@link #adjustRange}),
     * so there's never a partial or out-of-range state to reject here.
     */
    public boolean confirmRangeEdit() {
        boolean changed = !editZone.equals(zone != null ? zone : RangeBox.LEGACY_DEFAULT);
        this.zone = editZone;
        this.rangeEditMode = false;
        this.editZone = null;
        this.setChanged();
        this.syncToClient();
        return changed;
    }

    /**
     * Grows or shrinks {@link #editZone} by one cell facing {@code direction}, clamped to this
     * sensor's envelope (see {@link RangeBox#envelopeFor}) - a no-op if not currently editing.
     * Server-side only, called from the scroll-wheel packet handler; {@code openDirection} comes
     * from the caller (it needs the live {@link BlockState} to read {@code FACING}, which this
     * class has no reason to hold onto itself).
     */
    public void adjustRange(Direction direction, Direction openDirection, boolean grow) {
        if (!rangeEditMode || editZone == null) {
            return;
        }
        RangeBox next = grow ? editZone.grow(direction, openDirection) : editZone.shrink(direction, openDirection);
        if (!next.equals(editZone)) {
            this.editZone = next;
            this.syncToClient();
        }
    }

    /**
     * Called every tick by an external driver (a bound advanced sensor targeting this one) that
     * currently wants this sensor's signal held on. Only ever pushes the deadline forward
     * ({@code Math.max}), so multiple simultaneous drivers - or this call racing against this
     * sensor's own {@link #tick} in either order - can't undo each other; whichever deadline is
     * furthest out wins, and this sensor's own {@link #tick} is still the sole place that acts on it.
     */
    void holdExternalSignal(long untilGameTime) {
        this.externalHoldUntilGameTime = Math.max(this.externalHoldUntilGameTime, untilGameTime);
    }

    boolean isExternallyHeld(long now) {
        return now < externalHoldUntilGameTime;
    }

    /**
     * Pushes {@code shouldSignal} into this block's own {@code PRESENT} state (its own local
     * redstone output around its own position) if it changed. Shared with
     * {@link AdvancedSensorBlockEntity}'s bound-mode ticks, which compute {@code shouldSignal}
     * from whatever they're bound to (a reader accepting a nearby player's card, or another
     * sensor's own on/off pattern) rather than this sensor's own zone scan, but still want their
     * own signal to fire alongside whatever they're driving.
     */
    protected static void applyPresent(Level level, BlockPos pos, BlockState state, MotionSensorBlockEntity be, boolean shouldSignal) {
        if (!(state.getBlock() instanceof MotionSensorBlock sensor) || shouldSignal == state.getValue(MotionSensorBlock.PRESENT)) {
            return;
        }
        BlockState updated = state.setValue(MotionSensorBlock.PRESENT, shouldSignal);
        level.setBlock(pos, updated, Block.UPDATE_ALL);
        level.updateNeighborsAt(pos, state.getBlock());
        level.updateNeighborsAt(pos.relative(sensor.openDirection(updated).getOpposite()), state.getBlock());
        be.notifyLinkChanged();
    }

    @Override
    public SignalMode getSignalMode() {
        return linkState.getSignalMode();
    }

    /** Set by the wrench UI's normal/link/mixed mode buttons. */
    @Override
    public void setSignalMode(SignalMode signalMode) {
        linkState.setSignalMode(signalMode);
        this.syncToClient();
    }

    @Override
    public int getLinkStrength() {
        return getBlockState().getValue(MotionSensorBlock.PRESENT) ? 15 : 0;
    }

    @Override
    public ItemStack getFrequencySlot(int index) {
        return linkState.getFrequencySlot(index);
    }

    @Override
    public void setFrequencySlot(int index, ItemStack stack) {
        linkState.setFrequencySlot(index, stack);
        this.syncToClient();
    }

    @Override
    public int getSignalLength() {
        return signalLength;
    }

    @Override
    public void setSignalLength(int ticks) {
        this.signalLength = ticks;
        this.syncToClient();
    }

    /** Back to the documented default (10t, same as a stone button's press duration). */
    @Override
    public void clearSignalLength() {
        this.signalLength = 10;
        this.syncToClient();
    }

    @Override
    public boolean isWrenchPickupPending() {
        return wrenchPickup.isPending(level);
    }

    @Override
    public void armWrenchPickupPending() {
        wrenchPickup.arm(level);
    }

    @Override
    public void clearPendingActions() {
        wrenchPickup.clear();
    }

    /** A no-op unless Create is installed and this sensor is currently registered. */
    private void notifyLinkChanged() {
        linkState.notifyLinkChanged();
    }

    protected void syncToClient() {
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        linkState.onLoad();
        DeviceRegistry.registerOnLoad(this, deviceId);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        linkState.setRemoved();
    }

    /**
     * Shared {@code onRemove} body for {@link WallSensorBlock}/{@link CeilingSensorBlock} (and,
     * inherited, both advanced variants) - unregisters this sensor's {@link #deviceId} from
     * {@link DeviceIndex} once it's actually destroyed (not just moved).
     */
    static void onRemoved(Level level, BlockPos pos, BlockState state, BlockState newState, boolean moved) {
        if (moved || state.is(newState.getBlock())) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof MotionSensorBlockEntity sensor)) {
            return;
        }
        if (level.isClientSide) {
            ClientDeviceCache.unregister(sensor.deviceId);
        } else if (level instanceof ServerLevel serverLevel) {
            DeviceIndex.get(serverLevel).unregister(sensor.deviceId);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        DeviceRegistry.save(tag, deviceId);
        linkState.save(tag, registries);
        tag.putInt("SignalLength", signalLength);
        if (zone != null) {
            tag.put("Zone", writeRangeBox(zone));
        }
        // Goes through this same tag rather than a separate sync-only path because getUpdateTag
        // below just calls this same saveAdditional - anything left out of it would never reach
        // the client either, and the box would silently never render (a real bug an earlier
        // version of this had: tracked server-side with no way for the client to learn about it).
        if (rangeEditMode) {
            tag.putBoolean("RangeEditMode", true);
            if (editZone != null) {
                tag.put("EditZone", writeRangeBox(editZone));
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        deviceId = DeviceRegistry.load(tag);
        linkState.load(tag, registries);
        signalLength = tag.contains("SignalLength") ? tag.getInt("SignalLength") : 10;
        zone = tag.contains("Zone") ? readRangeBox(tag.getCompound("Zone")) : null;
        rangeEditMode = tag.getBoolean("RangeEditMode");
        editZone = tag.contains("EditZone") ? readRangeBox(tag.getCompound("EditZone")) : null;
        // only ever non-null here (as opposed to during a plain disk-chunk load, where the level
        // isn't attached until after this returns) when NBT is being pasted onto an already-placed
        // block - a Create schematic print, most notably. Re-registers unconditionally (not just on
        // a confirmed collision) since onLoad may already have run earlier in that same sequence,
        // before this deviceId was known, registering a since-discarded temporary one instead.
        if (level instanceof ServerLevel serverLevel) {
            // a plain sensor references nothing of its own, so a fresh id is the whole fix
            deviceId = DeviceRegistry.registerResolvingDuplicate(
                    this, serverLevel, DeviceIndex.get(serverLevel), deviceId, null);
        }
    }

    private static CompoundTag writeRangeBox(RangeBox box) {
        CompoundTag boxTag = new CompoundTag();
        boxTag.putInt("MinX", box.minX());
        boxTag.putInt("MaxX", box.maxX());
        boxTag.putInt("MinY", box.minY());
        boxTag.putInt("MaxY", box.maxY());
        boxTag.putInt("MinZ", box.minZ());
        boxTag.putInt("MaxZ", box.maxZ());
        return boxTag;
    }

    private static RangeBox readRangeBox(CompoundTag boxTag) {
        return new RangeBox(boxTag.getInt("MinX"), boxTag.getInt("MaxX"), boxTag.getInt("MinY"),
                boxTag.getInt("MaxY"), boxTag.getInt("MinZ"), boxTag.getInt("MaxZ"));
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
