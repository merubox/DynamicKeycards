package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DKSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A motion sensor mounted flush against a wall (8x2x2, a thin bar), never on a floor or
 * ceiling. Outputs a redstone signal for as long as a living entity is in the two-cell column
 * at its position (see {@link MotionSensorBlock#detectionZone}) - unlike the card reader, this
 * is a level (not pulsed) signal, matching an automatic-door sensor rather than a swipe reader.
 * Same wrench UI/pickup as the reader (see {@link WrenchConfigurableBlock}), minus anything
 * keycard-related - this block itself has no owner binding of its own, so anyone can wrench it
 * up regardless of who placed it, unless it's currently bound to a reader (see
 * {@code AdvancedSensorBlockEntity#getBoundReader}), in which case {@link #useItemOn} gates it by
 * that reader's own owner instead (see {@link MaintenanceAccess}).
 */
public class WallSensorBlock extends HorizontalDirectionalBlock implements EntityBlock, MotionSensorBlock, WrenchConfigurableBlock {

    // hugs the edge nearest whichever wall this FACING implies (pos.relative(facing.opposite())):
    // NORTH -> wall to the south -> hugs the south (high-Z) edge; SOUTH -> hugs north (low-Z).
    // Mirrored on the X axis: EAST -> wall to the west -> hugs the west (low-X) edge; WEST ->
    // hugs east (high-X). (EAST/WEST were swapped relative to this pattern until now - that's
    // what made the sensor attach backwards specifically on the east/west axis.)
    protected static final VoxelShape NORTH_SHAPE = Block.box(4, 7, 14, 12, 9, 16);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(4, 7, 0, 12, 9, 2);
    protected static final VoxelShape EAST_SHAPE = Block.box(0, 7, 4, 2, 9, 12);
    protected static final VoxelShape WEST_SHAPE = Block.box(14, 7, 4, 16, 9, 12);

    public WallSensorBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PRESENT, false));
    }

    @Override
    protected MapCodec<? extends WallSensorBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PRESENT);
    }

    @Override
    public Direction openDirection(BlockState state) {
        return state.getValue(FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return newMotionSensorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return motionSensorTicker(level, blockEntityType);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos wallPos = pos.relative(facing.getOpposite());
        return level.getBlockState(wallPos).isFaceSturdy(level, wallPos, facing);
    }

    /**
     * Attaches to whichever face was actually right-clicked. Deliberately not
     * {@code getNearestLookingDirections()} (what vanilla's {@code LadderBlock} uses): that
     * guesses from the player's view angle and doesn't always land on the clicked face, which
     * reads as the sensor attaching to the wrong wall.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis() == Direction.Axis.Y) {
            return null;
        }
        BlockState state = this.defaultBlockState().setValue(FACING, face);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                      LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!state.getValue(PRESENT) || !MotionSensorBlock.isPhysicalSignalActive(level, pos)) {
            return 0;
        }
        return 15;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!state.getValue(PRESENT) || openDirection(state) != direction || !MotionSensorBlock.isPhysicalSignalActive(level, pos)) {
            return 0;
        }
        return 15;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /**
     * A wrench-tagged item (any mod's - not Create-gated, see {@code CardReaderBlock}'s own
     * wrench-branch doc for why) or either maintenance card. Standing opens the config menu;
     * sneaking picks the sensor up after a confirming second click - gated by
     * {@link MaintenanceAccess} only while bound to a reader (a plain sensor, or an advanced one
     * that's unbound/bound to another sensor, has no owner in the relationship to protect).
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty()) {
            ItemInteractionResult bindResult = tryBindItemInteraction(stack, level, pos, player);
            if (bindResult != null) {
                return bindResult;
            }
            ItemInteractionResult rangeResult = tryRangeEditInteraction(stack, level, pos, player);
            if (rangeResult != null) {
                return rangeResult;
            }
        }
        boolean isWrench = stack.is(Tags.Items.TOOLS_WRENCH);
        if (stack.isEmpty() || !(isWrench || MaintenanceAccess.isMaintenanceCard(stack))) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof MotionSensorBlockEntity sensor)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        // only a sensor actually bound to a reader is gated at all - a plain sensor, or an
        // advanced one that's unbound/bound to another sensor, has no owner in the relationship
        // to protect (see MaintenanceAccess's own doc)
        UUID boundReaderId = sensor instanceof AdvancedSensorBlockEntity advanced ? advanced.getBoundReader() : null;
        if (!MaintenanceAccess.hasReaderLinkedAccess(player, stack, level, boundReaderId)) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("dynamickeycards.link_device.maintenance_denied").withStyle(ChatFormatting.RED), true);
                DKSounds.deny(level, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player.isShiftKeyDown()) {
            return wrenchPickup(state, level, pos, player, sensor);
        }
        return openLinkDeviceMenu(state, level, pos, player, sensor);
    }

    /** Bare-hand click cancels an armed range edit - see {@link #tryCancelRangeEdit}. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (tryCancelRangeEdit(level, pos, player)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        MotionSensorBlockEntity.onRemoved(level, pos, state, newState, moved);
        super.onRemove(state, level, pos, newState, moved);
    }
}
