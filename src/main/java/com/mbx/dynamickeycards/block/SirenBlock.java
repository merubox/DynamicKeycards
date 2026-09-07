package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DKSounds;
import com.mbx.dynamickeycards.item.SirenBlockItem;
import com.mbx.dynamickeycards.menu.SirenMenu;
import com.mbx.dynamickeycards.registry.DKBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A rotating warning light: spins while either the physical redstone reaching it or a bound
 * {@link SignalSource} is live, for a configurable turn speed and tail - see
 * {@link SirenBlockEntity}. Purely an output; it never puts out redstone of its own, so unlike
 * {@link ReceiverBlock} there's no {@code getSignal} here and no neighbor updates to push.
 *
 * <p>Standing + wrench (or either maintenance card) opens the config UI, sneaking picks it up -
 * see {@link TransmitterBlock}'s own class doc for why the wrench check isn't gated on
 * {@code CreateLinkCompat}. Gated by {@code MaintenanceAccess} on the same terms a receiver is:
 * only while its bound source actually resolves to a card reader right now.
 */
public class SirenBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock, WrenchPickupBlock {

    /**
     * The model's own three tiers rather than one enclosing box - shade 6x2x6, lens 4x4x4, tip
     * 2x1x2 - so the selection outline follows the silhouette instead of hanging in the air around
     * the narrow half. Each orientation is spelled out the way every other device here does it
     * (no rotation helper); they were generated from the floor shape with the same transforms
     * {@link ReceiverBlock}'s own hardcoded shapes are built on.
     */
    protected static final VoxelShape FLOOR_SHAPE = Shapes.or(
            Block.box(5, 0, 5, 11, 2, 11),
            Block.box(6, 2, 6, 10, 6, 10),
            Block.box(7, 6, 7, 9, 7, 9));
    protected static final VoxelShape CEILING_SHAPE = Shapes.or(
            Block.box(5, 14, 5, 11, 16, 11),
            Block.box(6, 10, 6, 10, 14, 10),
            Block.box(7, 9, 7, 9, 10, 9));
    protected static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Block.box(5, 5, 0, 11, 11, 2),
            Block.box(6, 6, 2, 10, 10, 6),
            Block.box(7, 7, 6, 9, 9, 7));
    protected static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(5, 5, 14, 11, 11, 16),
            Block.box(6, 6, 10, 10, 10, 14),
            Block.box(7, 7, 9, 9, 9, 10));
    protected static final VoxelShape WEST_SHAPE = Shapes.or(
            Block.box(14, 5, 5, 16, 11, 11),
            Block.box(10, 6, 6, 14, 10, 10),
            Block.box(9, 7, 7, 10, 9, 9));
    protected static final VoxelShape EAST_SHAPE = Shapes.or(
            Block.box(0, 5, 5, 2, 11, 11),
            Block.box(2, 6, 6, 6, 10, 10),
            Block.box(6, 7, 7, 7, 9, 9));

    /** Whether the light is turning - kept in step with {@link SirenBlockEntity#isSpinning}. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public SirenBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(LIT, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(FACE, AttachFace.FLOOR));
    }

    @Override
    protected MapCodec<? extends SirenBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE, LIT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SirenBlockEntity(pos, state);
    }

    /** Carries whatever the held item was tuned to into the new block entity - see {@link ReceiverBlock#setPlacedBy}. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof SirenBlockEntity siren)) {
            return;
        }
        UUID sourceId = SirenBlockItem.boundSource(stack);
        if (sourceId == null) {
            return;
        }
        siren.setBoundSource(sourceId);
        if (placer instanceof Player player) {
            player.displayClientMessage(
                    Component.translatable("dynamickeycards.link_device.linked").withStyle(ChatFormatting.GREEN), true);
            DKSounds.confirm(level, pos);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != DKBlockEntities.SIREN.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof SirenBlockEntity siren && SirenBlockEntity.tick(lvl, pos, siren)) {
                setLit(lvl, pos, st, siren.isSpinning());
            }
        };
    }

    /** Keeps {@link #LIT} in step with the spin, so the block shows what it's doing even without its renderer. */
    private static void setLit(Level level, BlockPos pos, BlockState state, boolean lit) {
        if (state.getValue(LIT) != lit) {
            level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
        }
    }

    /**
     * A wrench or either maintenance card: standing opens the config UI, sneaking picks it up
     * after a confirming second click - the same shape as {@link ReceiverBlock#useItemOn}.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !(stack.is(Tags.Items.TOOLS_WRENCH) || MaintenanceAccess.isMaintenanceCard(stack))) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof SirenBlockEntity siren)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!MaintenanceAccess.hasReaderLinkedAccess(player, stack, level, siren.getBoundSource())) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("dynamickeycards.link_device.maintenance_denied").withStyle(ChatFormatting.RED), true);
                DKSounds.deny(level, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player.isShiftKeyDown()) {
            return wrenchPickup(state, level, pos, player, siren);
        }
        return openSirenMenu(state, level, pos, player, siren);
    }

    /** Standing + wrench: opens the config menu, cancelling any pending wrench-pickup confirmation first - see {@link ReceiverBlock}. */
    private ItemInteractionResult openSirenMenu(BlockState state, Level level, BlockPos pos, Player player, SirenBlockEntity siren) {
        if (!level.isClientSide) {
            siren.clearPendingActions();
            if (player instanceof ServerPlayer serverPlayer) {
                MenuProvider provider = new SimpleMenuProvider(
                        (containerId, playerInventory, opener) -> new SirenMenu(containerId, playerInventory, siren),
                        state.getBlock().getName());
                serverPlayer.openMenu(provider, buf -> buf.writeBlockPos(pos));
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Holds onto anything solid through the middle rather than needing a whole flat face - the
     * lantern's rule ({@code SupportType.CENTER}), so a fence post, a wall or a chain will carry
     * one. What it replaces is the lever's rule inherited from
     * {@link FaceAttachedHorizontalDirectionalBlock}, which wants a full sturdy face and so turns
     * all of those down. A warning light is exactly the thing people bolt to a railing.
     */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction toSupport = getConnectedDirection(state).getOpposite();
        return Block.canSupportCenter(level, pos.relative(toSupport), toSupport.getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> FLOOR_SHAPE;
            case CEILING -> CEILING_SHAPE;
            case WALL -> switch (state.getValue(FACING)) {
                case SOUTH -> SOUTH_SHAPE;
                case EAST -> EAST_SHAPE;
                case WEST -> WEST_SHAPE;
                default -> NORTH_SHAPE;
            };
        };
    }
}
