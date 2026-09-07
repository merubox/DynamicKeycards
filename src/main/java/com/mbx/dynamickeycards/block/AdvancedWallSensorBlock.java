package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.registry.DKBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A strict superset of {@link WallSensorBlock}: same shape, placement, redstone and wrench
 * behavior (all inherited - only {@link #newBlockEntity}/{@link #getTicker} change, to use
 * {@link AdvancedSensorBlockEntity} instead), plus binding to a card reader before placement
 * (see {@code BoundSensorBlockItem} and {@link AdvancedSensorBlockEntity}) and dyeing (see
 * {@link AdvancedSensorDyeing}). Left unbound and undyed, it's indistinguishable in play from a
 * plain wall sensor.
 */
public class AdvancedWallSensorBlock extends WallSensorBlock implements AdvancedSensorDyeing {

    public AdvancedWallSensorBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends AdvancedWallSensorBlock> codec() {
        return MapCodec.unit(this);
    }

    /** Adds {@link #ACCENT} on top of the plain sensor's own properties. */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACCENT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdvancedSensorBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof AdvancedSensorBlockEntity sensor) {
            AdvancedSensorBlockEntity.applyPlacedBinding(level, pos, placer, sensor, stack);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != DKBlockEntities.ADVANCED_SENSOR.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof AdvancedSensorBlockEntity sensor) {
                AdvancedSensorBlockEntity.tick(lvl, pos, st, sensor);
            }
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty() && level.getBlockEntity(pos) instanceof AdvancedSensorBlockEntity sensor) {
            ItemInteractionResult dyeResult = tryDyeInteraction(stack, level, pos, player, sensor);
            if (dyeResult != null) {
                return dyeResult;
            }
            // no owner/register-mode gate - an advanced sensor has no such consent concept to begin with
            ItemInteractionResult bindResult = SignalSource.tryBindReceiverItem(stack, level, pos, player, sensor);
            if (bindResult != null) {
                return bindResult;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
