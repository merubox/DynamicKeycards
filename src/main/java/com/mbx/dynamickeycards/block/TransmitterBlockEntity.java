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

import java.util.UUID;

/**
 * A Create-independent wireless broadcaster: no local physical redstone output of its own (that
 * was a deliberate design choice - see {@code ROADMAP.md} - not a missing feature), it just
 * mirrors whatever it currently reads into {@link DeviceIndex} for a bound {@code ReceiverBlockEntity}
 * to pick up. Deliberately a pure, interpretation-free mirror: no tick-to-tick comparison or
 * duration/toggle judgment of its own happens here - see {@link #tick} for why that matters.
 */
public class TransmitterBlockEntity extends BlockEntity implements SignalSource, WrenchPickupTarget {

    /**
     * Default for {@link #manualTriggerTicks} - matches vanilla's own stone button
     * ({@code ButtonBlock}'s {@code ticksToStayPressed} for {@code BlockSetType.STONE}), on the
     * theory that a manual trigger is standing in for a physical button wired into this same
     * transmitter's redstone-only input - configurable rather than fixed specifically so a bound
     * receiver's own pulse-mode release delay has a real, adjustable duration to work with (a real
     * wired button's own press length already flows through naturally via {@link #tick}'s wire
     * read; this is that same idea for the manual trigger, which has no physical press length of
     * its own to inherit).
     */
    static final int DEFAULT_MANUAL_TRIGGER_TICKS = 20;


    private UUID deviceId = UUID.randomUUID();
    private TransmitterMode mode = TransmitterMode.REDSTONE_ONLY;
    private int manualTriggerTicks = DEFAULT_MANUAL_TRIGGER_TICKS;
    private long manualUntilGameTime = -1;
    /** {@code -1} until the first tick, so the very first read always counts as "changed" and gets published. */
    private int lastPublishedStrength = -1;
    private final WrenchPickupState wrenchPickup = new WrenchPickupState();

    public TransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(DKBlockEntities.TRANSMITTER.get(), pos, state);
    }

    public TransmitterMode getMode() {
        return mode;
    }

    public void setMode(TransmitterMode mode) {
        this.mode = mode;
        this.syncToClient();
    }

    @Override
    public UUID getDeviceId() {
        return deviceId;
    }

    @Override
    public int getSignalSourceStrength() {
        return Math.max(lastPublishedStrength, 0);
    }

    public int getManualTriggerTicks() {
        return manualTriggerTicks;
    }

    public void setManualTriggerTicks(int ticks) {
        this.manualTriggerTicks = Math.max(0, ticks);
        this.syncToClient();
    }

    /** Restores {@link #manualTriggerTicks} to {@link #DEFAULT_MANUAL_TRIGGER_TICKS} - the reset button's own action, see {@code TransmitterMenu.BUTTON_RESET}. */
    public void resetManualTriggerTicks() {
        setManualTriggerTicks(DEFAULT_MANUAL_TRIGGER_TICKS);
    }

    /** Called from {@code TransmitterBlock#useWithoutItem} while standing in {@link TransmitterMode#MIXED}. */
    void triggerManual(long now) {
        manualUntilGameTime = now + manualTriggerTicks;
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

    /**
     * Pure, interpretation-free mirror: combines the strongest redstone signal reaching it from any
     * side ({@code getBestNeighborSignal}) with
     * whether a manual trigger is still live, by simple max, and publishes only when that combined
     * value actually changes - no comparison against its own past state beyond that, no
     * duration/toggle logic. All duration/pulse/toggle shaping happens exclusively on the
     * receiving end (see {@code ReceiverBlockEntity#tick}) - a transmitter that instead judged its
     * own input (e.g. ran its own toggle logic) would conflict with a receiver bound to it also
     * toggling on the same edge, flipping twice for what should be one logical trigger.
     */
    static void tick(Level level, BlockPos pos, BlockState state, TransmitterBlockEntity be) {
        int wireStrength = level.getBestNeighborSignal(pos);
        int manualStrength = level.getGameTime() < be.manualUntilGameTime ? 15 : 0;
        int strength = Math.max(wireStrength, manualStrength);
        if (strength != be.lastPublishedStrength) {
            be.lastPublishedStrength = strength;
            be.publishSignalSource(level);
        }
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
        if (DeviceRegistry.registerOnLoad(this, deviceId) != null) {
            publishSignalSource(level);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        DeviceRegistry.save(tag, deviceId);
        tag.putString("Mode", mode.name());
        tag.putInt("ManualTriggerTicks", manualTriggerTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        deviceId = DeviceRegistry.load(tag);
        mode = TransmitterMode.byName(tag.getString("Mode"));
        manualTriggerTicks = tag.contains("ManualTriggerTicks") ? tag.getInt("ManualTriggerTicks") : DEFAULT_MANUAL_TRIGGER_TICKS;
        // only ever non-null here (as opposed to during a plain disk-chunk load, where the level
        // isn't attached until after this returns) when NBT is being pasted onto an already-placed
        // block - a Create schematic print, most notably. Re-registers unconditionally (not just on
        // a confirmed collision) since onLoad may already have run earlier in that same sequence,
        // before this deviceId was known, registering a since-discarded temporary one instead.
        if (level instanceof ServerLevel serverLevel) {
            // a transmitter references nothing of its own, so a fresh id is the whole fix; the
            // published strength is reset so the next tick republishes under the new identity
            deviceId = DeviceRegistry.registerResolvingDuplicate(
                    this, serverLevel, DeviceIndex.get(serverLevel), deviceId,
                    () -> lastPublishedStrength = -1);
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
