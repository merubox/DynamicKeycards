package com.mbx.dynamickeycards;

import com.mbx.dynamickeycards.block.MotionSensorBlock;
import com.mbx.dynamickeycards.block.MotionSensorBlockEntity;
import com.mbx.dynamickeycards.block.RangeBox;
import com.mbx.dynamickeycards.network.SensorRangeAdjustPayload;
import com.mbx.dynamickeycards.network.SensorRangeCommitPayload;
import com.mbx.dynamickeycards.registry.DKComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

/**
 * This mod's only two custom packets - see {@link SensorRangeAdjustPayload}/{@link SensorRangeCommitPayload}
 * for why raw mouse scroll and click input need them when nothing else here does.
 */
@EventBusSubscriber(modid = DynamicKeycards.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class DKNetwork {

    /**
     * How far a player may be from {@code pos} for a scroll-adjust/commit to be honored - sanity,
     * not precision, and generous enough to match {@code SensorRangeClientHandler}'s own render
     * distance (the box is meant to stay usable at whatever range it's still visible from).
     */
    private static final double MAX_ADJUST_DISTANCE_SQ = 48.0 * 48.0;

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SensorRangeAdjustPayload.TYPE, SensorRangeAdjustPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleAdjust(payload, context.player())));
        registrar.playToServer(SensorRangeCommitPayload.TYPE, SensorRangeCommitPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleCommit(payload, context.player())));
    }

    /**
     * Resolves which face to grow/shrink from the sending player's own current look ray against
     * the box actually being edited - a real ray-box hit test ({@link RangeBox#hitFace}), not
     * just rounding the raw look vector to the nearest axis direction: looking *at* a box's near
     * face means aiming *through* it, so the raw look vector roughly opposes that face's own
     * outward normal, and naively rounding it resolves to the *far* face instead - the actual
     * face the player was aiming at is only recoverable by testing the ray against the box's
     * real geometry. Also requires the player to actually be holding redstone dust - not just
     * trusting the client's own Ctrl+Scroll gating (see {@code SensorRangeClientHandler#onMouseScroll}
     * for why that alone isn't enough: Ctrl alone collides with vanilla's Sprint keybind).
     * Silently does nothing for any invalid state (too far away, not a sensor, not mid-edit, ray
     * misses the box entirely) - this is a continuous per-notch adjustment, not an action worth a
     * denial message every time.
     */
    private static void handleAdjust(SensorRangeAdjustPayload payload, Player player) {
        BlockPos pos = payload.pos();
        if (player.level().isClientSide || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_ADJUST_DISTANCE_SQ
                || (!player.getMainHandItem().is(Items.REDSTONE) && !player.getOffhandItem().is(Items.REDSTONE))) {
            return;
        }
        BlockState state = player.level().getBlockState(pos);
        if (!(state.getBlock() instanceof MotionSensorBlock sensorBlock)
                || !(player.level().getBlockEntity(pos) instanceof MotionSensorBlockEntity sensor)
                || !sensor.isRangeEditMode() || !(sensor.getEditZone() instanceof RangeBox editZone)) {
            return;
        }
        Direction face = editZone.hitFace(pos, player.getEyePosition(), player.getLookAngle());
        if (face == null) {
            return;
        }
        sensor.adjustRange(face, sensorBlock.openDirection(state), payload.grow());
    }

    /**
     * Right-click (confirm) or left-click (cancel) while the client's cursor was over the
     * highlight - see {@code SensorRangeClientHandler#onClickInput}. Re-validates the same ray
     * hit test server-side rather than trusting that the client's own check still holds by the
     * time this arrives. A confirm that doesn't actually change anything from what the sensor
     * was already detecting doesn't cost a redstone dust - see {@link MotionSensorBlockEntity#confirmRangeEdit}.
     */
    private static void handleCommit(SensorRangeCommitPayload payload, Player player) {
        BlockPos pos = payload.pos();
        if (player.level().isClientSide || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_ADJUST_DISTANCE_SQ) {
            return;
        }
        Level level = player.level();
        if (!(level.getBlockState(pos).getBlock() instanceof MotionSensorBlock)
                || !(level.getBlockEntity(pos) instanceof MotionSensorBlockEntity sensor)
                || !sensor.isRangeEditMode() || !(sensor.getEditZone() instanceof RangeBox editZone)
                || editZone.hitFace(pos, player.getEyePosition(), player.getLookAngle()) == null) {
            return;
        }
        if (payload.confirm()) {
            if (!MotionSensorBlock.canEditRange(level, sensor)) {
                return;
            }
            ItemStack redstone = taggedRedstoneInHand(player, pos);
            if (redstone == null) {
                return;
            }
            if (sensor.confirmRangeEdit()) {
                redstone.shrink(1);
            }
            player.displayClientMessage(
                    Component.translatable("dynamickeycards.sensor.range_confirmed").withStyle(ChatFormatting.GREEN), true);
            DKSounds.confirm(level, pos);
        } else {
            sensor.cancelRangeEdit();
            player.displayClientMessage(
                    Component.translatable("dynamickeycards.sensor.range_cancelled").withStyle(ChatFormatting.WHITE), true);
            DKSounds.remove(level, pos);
        }
        // MotionSensorBlock#clearRangeEditMarkers searches the whole inventory, not just
        // whichever hand triggered this - the tagged stack might be in the other one
        MotionSensorBlock.clearRangeEditMarkers(player, pos);
    }

    /** The specific redstone dust stack tagged for {@code pos} (see {@link DKComponents#RANGE_EDIT_TARGET}), if it's actually in a hand right now. */
    @Nullable
    private static ItemStack taggedRedstoneInHand(Player player, BlockPos pos) {
        if (player.getMainHandItem().is(Items.REDSTONE) && pos.equals(player.getMainHandItem().get(DKComponents.RANGE_EDIT_TARGET.get()))) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().is(Items.REDSTONE) && pos.equals(player.getOffhandItem().get(DKComponents.RANGE_EDIT_TARGET.get()))) {
            return player.getOffhandItem();
        }
        return null;
    }
}
