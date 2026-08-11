package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DKSounds;
import com.mbx.dynamickeycards.item.BoundSensorBlockItem;
import com.mbx.dynamickeycards.registry.DKBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Shared shape between {@link WallSensorBlock} and {@link CeilingSensorBlock}: both detect
 * living entities in a fixed column (their own cell plus the one directly below) and drive a
 * redstone signal from it, wall/button-style - strong power only into whatever they're mounted
 * on, weak power in every direction. {@link MotionSensorBlockEntity} does the actual per-tick
 * detection; this interface only holds what both blocks need to agree on to make that generic.
 */
public interface MotionSensorBlock {

    BooleanProperty PRESENT = BooleanProperty.create("present");

    /** Direction pointing away from this sensor's mounting surface, into open space. */
    Direction openDirection(BlockState state);

    /** Column scanned for entities: this block's own cell and the one directly below it. */
    default AABB detectionZone(BlockPos pos) {
        return new AABB(pos.getX(), pos.getY() - 1, pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
    }

    default BlockEntity newMotionSensorBlockEntity(BlockPos pos, BlockState state) {
        return new MotionSensorBlockEntity(pos, state);
    }

    default <T extends BlockEntity> BlockEntityTicker<T> motionSensorTicker(Level level, BlockEntityType<T> type) {
        if (level.isClientSide || type != DKBlockEntities.MOTION_SENSOR.get()) {
            return null;
        }
        return (lvl, pos, state, be) -> {
            if (be instanceof MotionSensorBlockEntity sensor) {
                MotionSensorBlockEntity.tick(lvl, pos, state, sensor);
            }
        };
    }

    /** Suppressed in link-only mode, so the wire stays silent while Create carries the signal instead. */
    static boolean isPhysicalSignalActive(BlockGetter level, BlockPos pos) {
        return !(level.getBlockEntity(pos) instanceof MotionSensorBlockEntity sensor) || sensor.getSignalMode().physicalActive;
    }

    /**
     * A held, still-unplaced advanced sensor item ({@link BoundSensorBlockItem}) binds it to
     * this block instead of any of the usual wrench handling - {@code null} if {@code stack}
     * isn't one, the same "not applicable" convention {@code AdvancedSensorDyeing#tryDyeInteraction}
     * uses. Works the same whether this block is a plain or advanced sensor - see
     * {@link AdvancedSensorBlockEntity#applyPlacedBinding} for what happens once the item is
     * actually placed. Reacts regardless of whether Create is loaded, unlike the wrench
     * handling below it - binding sensors together doesn't depend on Create at all.
     *
     * <p>Refuses outright if this block is an advanced sensor that's already bound to something
     * (a reader or another sensor) - an advanced sensor is bound to exactly one thing at a time,
     * on either end of the relationship. Without this check, binding a second sensor to an
     * already-bound one would silently steal its single outgoing slot and sever whatever it was
     * already driving (see {@link AdvancedSensorBlockEntity#applyPlacedBinding}'s own doc) -
     * surprising and destructive for no benefit, since the same reach is available by just
     * binding directly to the reader instead. A plain sensor target has no outgoing slot to
     * begin with, so it's never locked this way.
     */
    @Nullable
    default ItemInteractionResult tryBindItemInteraction(ItemStack stack, Level level, BlockPos pos, Player player) {
        if (!(stack.getItem() instanceof BoundSensorBlockItem sensorItem)) {
            return null;
        }
        if (level.getBlockEntity(pos) instanceof AdvancedSensorBlockEntity existing
                && (existing.getBoundReader() != null || existing.getBoundSensor() != null)) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("dynamickeycards.link_device.already_bound").withStyle(ChatFormatting.RED), true);
                DKSounds.deny(level, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            sensorItem.bindToSensor(stack, pos);
            // white, not green: this only tunes the held item, the actual connection isn't
            // "complete" (green) until it's placed - see AdvancedSensorBlockEntity#applyPlacedBinding.
            // No sound here - only the completed connection plays one.
            player.displayClientMessage(
                    Component.translatable("dynamickeycards.link_device.tuned").withStyle(ChatFormatting.WHITE), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
