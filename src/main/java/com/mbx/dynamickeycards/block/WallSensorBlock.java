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

/**
 * A motion sensor mounted flush against a wall (8x2x2, a thin bar), never on a floor or
 * ceiling. Outputs a redstone signal for as long as a living entity is in the two-cell column
 * at its position (see {@link MotionSensorBlock#detectionZone}) - unlike the card reader, this
 * is a level (not pulsed) signal, matching an automatic-door sensor rather than a swipe reader.
 * Same wrench UI/pickup as the reader (see {@link WrenchConfigurableBlock}), minus anything
 * keycard-related - no owner binding, so anyone can wrench it up, not just whoever placed it.
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
     * Attaches to whichever face was actually right-clicked - not, as an earlier version did,
     * a guess based on the player's general view angle ({@code getNearestLookingDirections()},
     * the same approach vanilla's own {@code LadderBlock} uses). That guess doesn't always land
     * on the face actually clicked, which read as the sensor attaching to the wrong wall.
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
     * Create wrench only - reacts only when Create is loaded, same as the reader (without
     * Create there's no Redstone Link network for the config UI's mode buttons to mean
     * anything). Standing opens the config menu; sneaking picks the sensor up after a
     * confirming second click. No ownership check - unlike the reader, a sensor has no
     * access-control config worth protecting.
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
