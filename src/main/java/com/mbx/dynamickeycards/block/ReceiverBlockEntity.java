package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.registry.DKBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Binds to exactly one {@link SignalSource} (a card reader, transmitter, or advanced sensor) and
 * mirrors its cached {@link DeviceIndex} value into a genuine physical redstone output (0-15,
 * vanilla-compatible) - see {@link #tick} for the two modes.
 */
public class ReceiverBlockEntity extends BlockEntity implements WrenchPickupTarget {


    private UUID deviceId = UUID.randomUUID();
    @Nullable
    private UUID boundSourceId;
    private ReceiverMode mode = ReceiverMode.PULSE;
    private int releaseDelayTicks;
    /** Toggle mode's own persistent output state - see {@link #tick}. */
    private boolean toggledOn;

    private int outputLevel;
    /** Last tick's cached source value, for toggle's rising-edge check - see {@link #tick}. */
    private int lastCachedValue;
    /** {@code false} until the first tick after load/(re)bind, so that first tick seeds {@link #lastCachedValue} instead of comparing against a stale/default one - see {@link #tick}. */
    private boolean initializedLastCached;
    /** Game time {@link #tick} last saw a nonzero cached value - pulse mode's release-delay window. */
    private long lastNonZeroGameTime = -1;
    private final WrenchPickupState wrenchPickup = new WrenchPickupState();

    public ReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(DKBlockEntities.RECEIVER.get(), pos, state);
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    @Nullable
    public UUID getBoundSource() {
        return boundSourceId;
    }

    public void setBoundSource(@Nullable UUID sourceId) {
        if (level instanceof ServerLevel serverLevel) {
            DeviceIndex index = DeviceIndex.get(serverLevel);
            if (this.boundSourceId != null) {
                index.unregisterReceiver(this.boundSourceId, this);
            }
            if (sourceId != null) {
                index.registerReceiver(sourceId, this);
            }
        }
        this.boundSourceId = sourceId;
        // a rebind (or unbind) must not compare against whatever the old source last reported -
        // see #tick for why a wrong "previous" value here could cause a spurious toggle flip
        this.initializedLastCached = false;
        this.syncToClient();
    }

    public ReceiverMode getMode() {
        return mode;
    }

    public void setMode(ReceiverMode mode) {
        this.mode = mode;
        this.syncToClient();
    }

    /**
     * How long (in ticks) pulse mode keeps its output held at the last value after the cached
     * source value drops to 0, before dropping to 0 itself - the exact same "release delay" concept
     * {@code MotionSensorBlockEntity#signalLength} already uses (see its own tooltip: "outputs a
     * continuous redstone signal while something is detected, with a configurable release delay").
     * {@code 0} = instant cutoff, the default - not a minimum on-duration measured from when the
     * source first went nonzero, since pulse mode already mirrors the source's own live value for
     * as long as it stays nonzero regardless of this setting; this only extends the tail after it ends.
     */
    public int getReleaseDelayTicks() {
        return releaseDelayTicks;
    }

    public void setReleaseDelayTicks(int ticks) {
        this.releaseDelayTicks = Math.max(0, ticks);
        this.syncToClient();
    }

    /** Restores {@link #releaseDelayTicks} to its default (0, instant cutoff) - the reset button's own action, see {@code ReceiverMenu.BUTTON_RESET}. */
    public void resetReleaseDelayTicks() {
        setReleaseDelayTicks(0);
    }

    /** The physical redstone strength this receiver currently outputs (0-15). */
    public int getOutputLevel() {
        return outputLevel;
    }

    /** @return whether the output actually changed - so the caller knows whether to notify neighbors. */
    private boolean setOutputLevel(int value) {
        if (value == outputLevel) {
            return false;
        }
        outputLevel = value;
        this.setChanged();
        return true;
    }

    /**
     * Reads this tick's cached source value from {@link DeviceIndex} (a plain hashmap lookup -
     * never touches the source's own {@code BlockEntity}) and turns it into this receiver's own
     * output, per {@link #mode}:
     * <ul>
     *   <li><b>{@link ReceiverMode#TOGGLE}</b> - flips {@link #toggledOn} only on a 0-to-nonzero
     *   transition of the cached value, strictly in that direction. The falling (nonzero-to-0)
     *   direction is never also treated as an edge - a receiver bound to a momentary source (e.g.
     *   a card reader's brief accept pulse) would otherwise see both the rising and falling edge
     *   within the same short window and flip twice, cancelling itself back to the original state
     *   and defeating the entire point of toggle mode.</li>
     *   <li><b>{@link ReceiverMode#PULSE}</b> - outputs the cached value directly while it's
     *   nonzero; once it drops to 0, holds the last output for {@link #releaseDelayTicks} more
     *   ticks (0 = instant cutoff, the default) before dropping to 0 itself.</li>
     * </ul>
     *
     * @return whether {@link #outputLevel} changed this tick.
     */
    static boolean tick(Level level, BlockPos pos, BlockState state, ReceiverBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel) || be.boundSourceId == null) {
            be.initializedLastCached = false;
            return be.setOutputLevel(0);
        }
        int cached = DeviceIndex.get(serverLevel).getSignal(be.boundSourceId);
        // first tick after load/(re)bind: seed rather than compare, so an already-active source
        // doesn't read as a spurious rising edge the instant this receiver starts watching it
        int previousCached = be.initializedLastCached ? be.lastCachedValue : cached;
        be.lastCachedValue = cached;
        be.initializedLastCached = true;

        if (be.mode == ReceiverMode.TOGGLE) {
            if (previousCached == 0 && cached != 0) {
                be.toggledOn = !be.toggledOn;
                be.setChanged();
            }
            return be.setOutputLevel(be.toggledOn ? 15 : 0);
        }

        long now = level.getGameTime();
        if (cached != 0) {
            be.lastNonZeroGameTime = now;
            return be.setOutputLevel(cached);
        }
        if (be.releaseDelayTicks > 0 && be.lastNonZeroGameTime >= 0 && now - be.lastNonZeroGameTime < be.releaseDelayTicks) {
            // still within the release-delay window - leave the last output level exactly as it was
            return false;
        }
        return be.setOutputLevel(0);
    }

    /**
     * Called by {@link DeviceIndex#updateSignal} the instant our bound source's cached value
     * actually changes, so this receiver reacts the same game tick the change happens instead of
     * waiting out its own next scheduled tick. Just re-runs the exact same {@link #tick} logic
     * (which reads the value {@link DeviceIndex} already updated before calling this) through
     * {@code ReceiverBlock#onReceiverUpdated} - the regular ticker still runs too, since it alone
     * drives pulse mode's release-delay expiry (see {@link #tick}), which isn't triggered by any
     * value change at all.
     */
    void onSourceSignalChanged(ServerLevel level) {
        if (this.level == level && getBlockState().getBlock() instanceof ReceiverBlock receiverBlock) {
            receiverBlock.onReceiverUpdated(level, worldPosition, getBlockState(), this);
        }
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

    private void syncToClient() {
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        DeviceIndex index = DeviceRegistry.registerOnLoad(this, deviceId);
        if (index != null && boundSourceId != null) {
            index.registerReceiver(boundSourceId, this);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        DeviceRegistry.save(tag, deviceId);
        if (boundSourceId != null) {
            tag.putUUID("BoundSource", boundSourceId);
        }
        tag.putString("Mode", mode.name());
        tag.putInt("ReleaseDelayTicks", releaseDelayTicks);
        tag.putBoolean("ToggledOn", toggledOn);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        deviceId = DeviceRegistry.load(tag);
        boundSourceId = tag.hasUUID("BoundSource") ? tag.getUUID("BoundSource") : null;
        mode = ReceiverMode.byName(tag.getString("Mode"));
        releaseDelayTicks = tag.getInt("ReleaseDelayTicks");
        toggledOn = tag.getBoolean("ToggledOn");
        // only ever non-null here (as opposed to during a plain disk-chunk load, where the level
        // isn't attached until after this returns) when NBT is being pasted onto an already-placed
        // block - a Create schematic print, most notably. Re-registers unconditionally (not just on
        // a confirmed collision) since onLoad may already have run earlier in that same sequence -
        // before boundSourceId was known (still its pre-load default, null), so its own
        // registerReceiver call above could easily have been skipped entirely.
        if (level instanceof ServerLevel serverLevel) {
            DeviceIndex index = DeviceIndex.get(serverLevel);
            // boundSourceId is left untouched on a supersede, and the cached source value is
            // dropped since it belonged to the old identity
            deviceId = DeviceRegistry.registerResolvingDuplicate(
                    this, serverLevel, index, deviceId, () -> initializedLastCached = false);
            if (boundSourceId != null) {
                index.registerReceiver(boundSourceId, this);
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
