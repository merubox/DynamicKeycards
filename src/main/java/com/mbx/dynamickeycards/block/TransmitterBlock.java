package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DKSounds;
import com.mbx.dynamickeycards.DKTooltips;
import com.mbx.dynamickeycards.menu.TransmitterMenu;
import com.mbx.dynamickeycards.registry.DKBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A Create-independent wireless broadcaster, placeable on floor/wall/ceiling exactly like
 * {@link CardReaderBlock} (same base class, same free {@code canSurvive}/placement) - see
 * {@link #FLOOR_SHAPE_X}'s own doc for the (widened, chamfered) shape. Deliberately has no local
 * physical redstone output of its own - see {@link TransmitterBlockEntity}'s own doc. Standing +
 * wrench (or either maintenance card) opens the mode config UI, sneaking + either picks it up
 * (same gesture as {@link WrenchConfigurableBlock}, though this block doesn't implement that
 * interface itself - it isn't a {@link LinkDeviceBlockEntity}, so there's no {@code LinkDeviceMenu}
 * to open). The wrench branch isn't tied to {@code CreateLinkCompat} being loaded - a transmitter
 * has nothing to do with Create's Redstone Link either way.
 * A maintenance card is what actually closes the "what should open this UI in a Create-less game"
 * gap for a player with no wrench-tagged item at all - either card works, since a transmitter is
 * a lock-free device (no owner) and both cards act as plain wrench-equivalents on anything
 * unlocked; only a reader and the peripherals linked to one check whose card it is, see
 * {@link MaintenanceAccess}. Standing + empty hand in {@link TransmitterMode#MIXED} fires
 * a manual trigger.
 */
public class TransmitterBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock, WrenchPickupBlock {

    /**
     * Widened 2px per side over the receiver's plain 6-wide footprint (5-11 &#8594; 3-13,
     * still centered) for a silhouette that reads apart from it at a glance, tapering thinner
     * toward the two outer edges (1px/1.5px/2px-original-center/1.5px/1px) rather than a flat
     * slab - purely cosmetic, see {@code ReceiverBlock} for the untouched comparison shape.
     * Always widened along whichever axis reads as "가로" (horizontal, left-right) from a
     * player standing in front of the device, while the other in-plane axis and the thickness
     * axis (perpendicular to the mounting surface, tapered in the direction pointing *away*
     * from the surface) keep the original 6px/2px extents.
     *
     * <p>Which axis that is depends on {@link #FACING}, not just on {@link #FACE}: the
     * blockstate turns the model by 90 degrees for an east/west {@code facing}, on a floor or
     * ceiling mount just as much as on a wall, so the shape has to turn with it. Hence a
     * separate {@code _X} and {@code _Z} shape for every mounting surface - north/south take
     * the X pair, east/west the Z pair.
     */
    protected static final VoxelShape FLOOR_SHAPE_X = Shapes.or(
            Block.box(3, 0, 5, 4, 1, 11),
            Block.box(4, 0, 5, 5, 1.5, 11),
            Block.box(5, 0, 5, 11, 2, 11),
            Block.box(11, 0, 5, 12, 1.5, 11),
            Block.box(12, 0, 5, 13, 1, 11));
    protected static final VoxelShape FLOOR_SHAPE_Z = Shapes.or(
            Block.box(5, 0, 3, 11, 1, 4),
            Block.box(5, 0, 4, 11, 1.5, 5),
            Block.box(5, 0, 5, 11, 2, 11),
            Block.box(5, 0, 11, 11, 1.5, 12),
            Block.box(5, 0, 12, 11, 1, 13));
    protected static final VoxelShape CEILING_SHAPE_X = Shapes.or(
            Block.box(3, 15, 5, 4, 16, 11),
            Block.box(4, 14.5, 5, 5, 16, 11),
            Block.box(5, 14, 5, 11, 16, 11),
            Block.box(11, 14.5, 5, 12, 16, 11),
            Block.box(12, 15, 5, 13, 16, 11));
    protected static final VoxelShape CEILING_SHAPE_Z = Shapes.or(
            Block.box(5, 15, 3, 11, 16, 4),
            Block.box(5, 14.5, 4, 11, 16, 5),
            Block.box(5, 14, 5, 11, 16, 11),
            Block.box(5, 14.5, 11, 11, 16, 12),
            Block.box(5, 15, 12, 11, 16, 13));
    protected static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(3, 5, 15, 4, 11, 16),
            Block.box(4, 5, 14.5, 5, 11, 16),
            Block.box(5, 5, 14, 11, 11, 16),
            Block.box(11, 5, 14.5, 12, 11, 16),
            Block.box(12, 5, 15, 13, 11, 16));
    protected static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Block.box(3, 5, 0, 4, 11, 1),
            Block.box(4, 5, 0, 5, 11, 1.5),
            Block.box(5, 5, 0, 11, 11, 2),
            Block.box(11, 5, 0, 12, 11, 1.5),
            Block.box(12, 5, 0, 13, 11, 1));
    protected static final VoxelShape WEST_SHAPE = Shapes.or(
            Block.box(15, 5, 3, 16, 11, 4),
            Block.box(14.5, 5, 4, 16, 11, 5),
            Block.box(14, 5, 5, 16, 11, 11),
            Block.box(14.5, 5, 11, 16, 11, 12),
            Block.box(15, 5, 12, 16, 11, 13));
    protected static final VoxelShape EAST_SHAPE = Shapes.or(
            Block.box(0, 5, 3, 1, 11, 4),
            Block.box(0, 5, 4, 1.5, 11, 5),
            Block.box(0, 5, 5, 2, 11, 11),
            Block.box(0, 5, 11, 1.5, 11, 12),
            Block.box(0, 5, 12, 1, 11, 13));

    /** Whether the face light is on - driven from whatever this transmitter is currently broadcasting. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public TransmitterBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(LIT, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(FACE, AttachFace.WALL));
    }

    @Override
    protected MapCodec<? extends TransmitterBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE, LIT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransmitterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != DKBlockEntities.TRANSMITTER.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof TransmitterBlockEntity transmitter) {
                tickAndUpdateLight(lvl, pos, st, transmitter);
            }
        };
    }

    /**
     * A still-unplaced {@link com.mbx.dynamickeycards.item.ReceiverBlockItem} binds to this
     * transmitter instead of the usual interaction below - no owner concept here, so no consent
     * gate. A wrench, regardless of item type otherwise: standing opens the config UI, sneaking
     * picks it up after a confirming second click - see the class doc for why this isn't gated on
     * {@code CreateLinkCompat}.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Either maintenance card works here, same as on every other lock-free device (the
        // sensors, the receiver, the card duplicator) - a transmitter has no owner, so there's
        // nothing for the estate card to have to *match*; it just acts as the wrench-equivalent
        // it is on anything unlocked. Only a reader, and the peripherals actually linked to one,
        // check whose card it is - see MaintenanceAccess.
        if (stack.is(Tags.Items.TOOLS_WRENCH) || MaintenanceAccess.isMaintenanceCard(stack)) {
            if (!(level.getBlockEntity(pos) instanceof TransmitterBlockEntity transmitter)) {
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            }
            if (player.isShiftKeyDown()) {
                return wrenchPickup(state, level, pos, player, transmitter);
            }
            return openTransmitterMenu(state, level, pos, player, transmitter);
        }
        if (level.getBlockEntity(pos) instanceof TransmitterBlockEntity transmitter) {
            ItemInteractionResult bindResult = SignalSource.tryBindReceiverItem(stack, level, pos, player, transmitter);
            if (bindResult != null) {
                return bindResult;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Runs the transmitter's own tick, then keeps {@link #LIT} in step with what it is broadcasting
     * - a transmitter has no redstone output of its own, so the face light is the only thing that
     * shows it is doing anything at all.
     */
    private static void tickAndUpdateLight(Level level, BlockPos pos, BlockState state, TransmitterBlockEntity transmitter) {
        TransmitterBlockEntity.tick(level, pos, state, transmitter);
        boolean lit = transmitter.getSignalSourceStrength() > 0;
        if (state.getValue(LIT) != lit) {
            level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
        }
    }

    /** Standing + wrench: opens the config menu, cancelling any pending wrench-pickup confirmation first - see {@code WrenchConfigurableBlock.openLinkDeviceMenu}. */
    private ItemInteractionResult openTransmitterMenu(BlockState state, Level level, BlockPos pos, Player player, TransmitterBlockEntity transmitter) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            transmitter.clearPendingActions();
            MenuProvider provider = new SimpleMenuProvider(
                    (containerId, playerInventory, opener) -> new TransmitterMenu(containerId, playerInventory, transmitter),
                    state.getBlock().getName());
            serverPlayer.openMenu(provider, buf -> buf.writeBlockPos(pos));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Only reacts in {@link TransmitterMode#MIXED} - a manual trigger, exactly as if the
     * signal reaching it had briefly gone live; in {@link TransmitterMode#REDSTONE_ONLY} this does
     * nothing.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TransmitterBlockEntity transmitter)
                || transmitter.getMode() != TransmitterMode.MIXED) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            transmitter.triggerManual(level.getGameTime());
            // null rather than the pressing player: unlike vanilla's button, nothing here is
            // client-predicted, so the presser has to be sent the sound too
            DKSounds.manualTrigger(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Without this, a wired input change would only ever be noticed on this transmitter's own
     * next scheduled tick (up to a tick late, since {@link #getTicker} polls
     * {@link Level#getBestNeighborSignal} rather than being pushed the change) - reacting here
     * instead publishes the new value the instant the wire actually changes, same as how vanilla's
     * own instantly-reactive redstone components (doors, pistons, ...) work, rather than a
     * comparator/observer's deliberate built-in delay.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, neighborPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TransmitterBlockEntity transmitter) {
            tickAndUpdateLight(level, pos, state, transmitter);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!moved && !state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TransmitterBlockEntity transmitter) {
            if (level.isClientSide) {
                ClientDeviceCache.unregister(transmitter.getDeviceId());
            } else if (level instanceof ServerLevel serverLevel) {
                DeviceIndex.get(serverLevel).unregister(transmitter.getDeviceId());
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        boolean widenedAlongZ = facing == Direction.EAST || facing == Direction.WEST;
        return switch (state.getValue(FACE)) {
            case FLOOR -> widenedAlongZ ? FLOOR_SHAPE_Z : FLOOR_SHAPE_X;
            case CEILING -> widenedAlongZ ? CEILING_SHAPE_Z : CEILING_SHAPE_X;
            case WALL -> switch (facing) {
                case SOUTH -> SOUTH_SHAPE;
                case EAST -> EAST_SHAPE;
                case WEST -> WEST_SHAPE;
                default -> NORTH_SHAPE;
            };
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        DKTooltips.summary(tooltip, "transmitter1", "transmitter2", "transmitter3");
    }
}
