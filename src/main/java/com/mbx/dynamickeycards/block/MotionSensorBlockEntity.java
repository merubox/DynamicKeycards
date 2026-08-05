package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.compat.create.CreateLinkCompat;
import com.mbx.dynamickeycards.registry.DKBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

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

    /** Same shape as {@code CardReaderBlockEntity}'s equivalent - see there for why. */
    private static final int PENDING_CONFIRM_TICKS = 60;

    private long wrenchPickupPendingStartTime = -1;
    /** Game time an entity was last detected; {@code -1} if none has been detected yet. */
    private long lastDetectedGameTime = -1;

    private final ItemStack[] frequencySlots = {ItemStack.EMPTY, ItemStack.EMPTY};
    private SignalMode signalMode = SignalMode.NORMAL;
    /** Release delay in ticks after the last detected entity leaves; 0 cuts the signal instantly. */
    private int signalLength = 10;
    @Nullable
    private Object createLinkAdapter;

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
        if (!(state.getBlock() instanceof MotionSensorBlock sensor)) {
            return;
        }
        boolean detected = !level.getEntitiesOfClass(LivingEntity.class, sensor.detectionZone(pos),
                MotionSensorBlockEntity::countsAsPresent).isEmpty();
        long now = level.getGameTime();
        if (detected) {
            be.lastDetectedGameTime = now;
        }
        // 0t: no lingering, drops the instant nothing's detected. >0t: keeps signalling until
        // that many ticks have passed since the last detection, resetting if something re-enters.
        boolean shouldSignal = detected || (be.signalLength > 0 && be.lastDetectedGameTime >= 0
                && now - be.lastDetectedGameTime < be.signalLength);
        applyPresent(level, pos, state, be, shouldSignal);
    }

    /**
     * Pushes {@code shouldSignal} into this block's own {@code PRESENT} state (its own local
     * redstone output around its own position) if it changed. Shared with
     * {@link AdvancedSensorBlockEntity}'s bound-mode tick, which computes {@code shouldSignal}
     * from whether the bound reader would accept a nearby player's card rather than from this
     * sensor's own {@link #detectionZone} scan, but still wants its own signal to fire alongside
     * the reader's.
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
        return signalMode;
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
        return getBlockState().getValue(MotionSensorBlock.PRESENT) ? 15 : 0;
    }

    @Override
    public ItemStack getFrequencySlot(int index) {
        return frequencySlots[index];
    }

    @Override
    public void setFrequencySlot(int index, ItemStack stack) {
        frequencySlots[index] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        reregisterLink();
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
        return wrenchPickupPendingStartTime >= 0 && level != null
                && level.getGameTime() - wrenchPickupPendingStartTime < PENDING_CONFIRM_TICKS;
    }

    @Override
    public void armWrenchPickupPending() {
        wrenchPickupPendingStartTime = level != null ? level.getGameTime() : -1;
    }

    @Override
    public void clearPendingActions() {
        wrenchPickupPendingStartTime = -1;
    }

    /** Re-announces this sensor to Create's Redstone Link network under its current frequency. */
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

    /** A no-op unless Create is installed and this sensor is currently registered. */
    private void notifyLinkChanged() {
        if (createLinkAdapter != null && level != null) {
            CreateLinkCompat.notifyChanged(level, createLinkAdapter);
        }
    }

    protected void syncToClient() {
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("SignalMode", signalMode.name());
        tag.putInt("SignalLength", signalLength);
        if (!frequencySlots[0].isEmpty()) {
            tag.put("FrequencySlot0", frequencySlots[0].save(registries));
        }
        if (!frequencySlots[1].isEmpty()) {
            tag.put("FrequencySlot1", frequencySlots[1].save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        signalMode = tag.contains("SignalMode") ? SignalMode.byName(tag.getString("SignalMode")) : SignalMode.NORMAL;
        signalLength = tag.contains("SignalLength") ? tag.getInt("SignalLength") : 10;
        frequencySlots[0] = tag.contains("FrequencySlot0")
                ? ItemStack.parseOptional(registries, tag.getCompound("FrequencySlot0")) : ItemStack.EMPTY;
        frequencySlots[1] = tag.contains("FrequencySlot1")
                ? ItemStack.parseOptional(registries, tag.getCompound("FrequencySlot1")) : ItemStack.EMPTY;
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
