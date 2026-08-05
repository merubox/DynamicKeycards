package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.registry.DKBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;

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
}
