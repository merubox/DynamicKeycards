package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.compat.create.CreateLinkCompat;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

/**
 * A motion sensor mounted flush against a ceiling (6x6, max thickness 2), never on a floor or
 * wall. Fully symmetric under horizontal rotation, so unlike {@link WallSensorBlock} it needs
 * no {@code FACING} - only placement (looking up) and support (a sturdy block above) differ.
 * Same level-signal detection as the wall variant, see {@link MotionSensorBlock}, and the same
 * wrench UI/pickup as the reader, see {@link WrenchConfigurableBlock}.
 *
 * <p>Not a flat panel: the 4x4 interior (rows/columns 2-5) sits at the full 2-thick depth, like a
 * raised sensor eye, while the outer ring - the whole of row 1 and row 6, plus columns 1 and 6 of
 * rows 2-5 - is a thin 1-thick mounting rim flush against the ceiling.
 */
public class CeilingSensorBlock extends Block implements EntityBlock, MotionSensorBlock, WrenchConfigurableBlock {

    protected static final VoxelShape SHAPE = Shapes.or(
            Block.box(5, 15, 5, 11, 16, 6),    // row 1, all columns
            Block.box(5, 15, 10, 11, 16, 11),  // row 6, all columns
            Block.box(5, 15, 6, 6, 16, 7),      // row 2, column 1
            Block.box(10, 15, 6, 11, 16, 7),    // row 2, column 6
            Block.box(5, 15, 7, 6, 16, 8),      // row 3, column 1
            Block.box(10, 15, 7, 11, 16, 8),    // row 3, column 6
            Block.box(5, 15, 8, 6, 16, 9),      // row 4, column 1
            Block.box(10, 15, 8, 11, 16, 9),    // row 4, column 6
            Block.box(5, 15, 9, 6, 16, 10),     // row 5, column 1
            Block.box(10, 15, 9, 11, 16, 10),   // row 5, column 6
            Block.box(6, 14, 6, 10, 16, 10)     // interior 4x4, full thickness
    );

    public CeilingSensorBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(PRESENT, false));
    }

    @Override
    protected MapCodec<? extends CeilingSensorBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PRESENT);
    }

    @Override
    public Direction openDirection(BlockState state) {
        return Direction.DOWN;
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
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos ceilingPos = pos.above();
        return level.getBlockState(ceilingPos).isFaceSturdy(level, ceilingPos, Direction.DOWN);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction != Direction.UP) {
                continue;
            }
            BlockState state = this.defaultBlockState();
            if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
                return state;
            }
        }
        return null;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                      LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP && !state.canSurvive(level, pos)) {
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
        if (!state.getValue(PRESENT) || direction != Direction.DOWN || !MotionSensorBlock.isPhysicalSignalActive(level, pos)) {
            return 0;
        }
        return 15;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /**
     * Create wrench only, same reasoning and behavior as {@link WallSensorBlock#useItemOn} -
     * standing opens the config menu, sneaking picks the sensor up after a confirming second
     * click, no ownership check.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !CreateLinkCompat.isLoaded() || !stack.is(Tags.Items.TOOLS_WRENCH)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof MotionSensorBlockEntity sensor)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (player.isShiftKeyDown()) {
            return wrenchPickup(state, level, pos, player, sensor);
        }
        return openLinkDeviceMenu(state, level, pos, player, sensor);
    }
}
