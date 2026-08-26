package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DKSounds;
import com.mbx.dynamickeycards.item.ReceiverBlockItem;
import com.mbx.dynamickeycards.menu.ReceiverMenu;
import com.mbx.dynamickeycards.registry.DKBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Binds (via a still-unplaced {@link com.mbx.dynamickeycards.item.ReceiverBlockItem}) to exactly
 * one {@link SignalSource} and mirrors it into a genuine physical redstone output (0-15, unlike
 * the card reader's on/off) - see {@link ReceiverBlockEntity} for the two modes. Plain 6x6x2
 * shape (deliberately left untouched, unlike {@link TransmitterBlock}'s own widened/chamfered
 * one - see its {@code FLOOR_SHAPE} doc for why) and the same floor/wall/ceiling placement.
 * Standing + wrench (or either maintenance card) opens the mode config UI, sneaking picks it up -
 * see {@link TransmitterBlock}'s own class doc for why the wrench check isn't gated on
 * {@code CreateLinkCompat}. A golden maintenance card always bypasses everything here too, same
 * as every other device; an estate maintenance card only matters (has to actually match an
 * owner) while {@link #useItemOn}'s bound-source resolves to a card reader right now - see
 * {@code MaintenanceAccess}'s own doc.
 */
public class ReceiverBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock, WrenchPickupBlock {

    protected static final VoxelShape FLOOR_SHAPE = Block.box(5, 0, 5, 11, 2, 11);
    protected static final VoxelShape CEILING_SHAPE = Block.box(5, 14, 5, 11, 16, 11);
    protected static final VoxelShape NORTH_SHAPE = Block.box(5, 5, 14, 11, 11, 16);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(5, 5, 0, 11, 11, 2);
    protected static final VoxelShape WEST_SHAPE = Block.box(14, 5, 5, 16, 11, 11);
    protected static final VoxelShape EAST_SHAPE = Block.box(0, 5, 5, 2, 11, 11);

    /** Whether the face light is on - driven by {@link #onReceiverUpdated} from this receiver's own output. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public ReceiverBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(LIT, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(FACE, AttachFace.WALL));
    }

    @Override
    protected MapCodec<? extends ReceiverBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE, LIT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReceiverBlockEntity(pos, state);
    }

    /**
     * Carries whatever {@link ReceiverBlockItem#bindTo} tuned the held item to (see
     * {@link SignalSource#tryBindReceiverItem}) into the new block entity - without this, a
     * placed receiver would stay unbound forever regardless of what its item was tuned to.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof ReceiverBlockEntity receiver)) {
            return;
        }
        UUID sourceId = ReceiverBlockItem.boundSource(stack);
        if (sourceId == null) {
            return;
        }
        receiver.setBoundSource(sourceId);
        if (placer instanceof Player player) {
            // green: unlike the white "tuned" message shown when the item was bound, this is
            // the point the connection actually exists - same convention as CardReaderBlock/
            // AdvancedSensorBlockEntity's own placed-binding announcements
            player.displayClientMessage(
                    Component.translatable("dynamickeycards.link_device.linked").withStyle(ChatFormatting.GREEN), true);
            DKSounds.confirm(level, pos);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != DKBlockEntities.RECEIVER.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof ReceiverBlockEntity receiver) {
                onReceiverUpdated(lvl, pos, st, receiver);
            }
        };
    }

    /**
     * Runs {@link ReceiverBlockEntity#tick} and, if the output actually changed, notifies redstone
     * neighbors - shared by this block's own regular ticker above (needed for pulse mode's
     * release-delay expiry, which isn't triggered by any value change at all) and
     * {@link ReceiverBlockEntity#onSourceSignalChanged} (an immediate push the instant the bound
     * source's value changes, so this receiver doesn't have to wait out its own next tick to notice).
     */
    void onReceiverUpdated(Level level, BlockPos pos, BlockState state, ReceiverBlockEntity receiver) {
        if (ReceiverBlockEntity.tick(level, pos, state, receiver)) {
            setLit(level, pos, state, receiver.getOutputLevel() > 0);
            updateNeighbors(state, level, pos);
        }
    }

    /** Keeps {@link #LIT} in step with the output, so the face texture shows what the block is doing. */
    private static void setLit(Level level, BlockPos pos, BlockState state, boolean lit) {
        if (state.getValue(LIT) != lit) {
            level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
        }
    }

    /**
     * A wrench or either maintenance card: standing opens the config UI, sneaking picks it up
     * after a confirming second click - same shape as {@link TransmitterBlock#useItemOn}, see
     * its class doc for why the wrench branch isn't gated on Create being loaded. Only gated by
     * {@link MaintenanceAccess} when {@link ReceiverBlockEntity#getBoundSource} actually resolves
     * to a card reader right now - a receiver bound to a transmitter, an advanced sensor, or
     * nothing at all stays open to anyone, same as before.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !(stack.is(Tags.Items.TOOLS_WRENCH) || MaintenanceAccess.isMaintenanceCard(stack))) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof ReceiverBlockEntity receiver)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!MaintenanceAccess.hasReaderLinkedAccess(player, stack, level, receiver.getBoundSource())) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("dynamickeycards.link_device.maintenance_denied").withStyle(ChatFormatting.RED), true);
                DKSounds.deny(level, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player.isShiftKeyDown()) {
            return wrenchPickup(state, level, pos, player, receiver);
        }
        return openReceiverMenu(state, level, pos, player, receiver);
    }

    /** Standing + wrench: opens the config menu, cancelling any pending wrench-pickup confirmation first - see {@code WrenchConfigurableBlock.openLinkDeviceMenu}. */
    private ItemInteractionResult openReceiverMenu(BlockState state, Level level, BlockPos pos, Player player, ReceiverBlockEntity receiver) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            receiver.clearPendingActions();
            MenuProvider provider = new SimpleMenuProvider(
                    (containerId, playerInventory, opener) -> new ReceiverMenu(containerId, playerInventory, receiver),
                    state.getBlock().getName());
            serverPlayer.openMenu(provider, buf -> buf.writeBlockPos(pos));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!moved && !state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ReceiverBlockEntity receiver) {
            if (level.isClientSide) {
                ClientDeviceCache.unregister(receiver.getDeviceId());
            } else if (level instanceof ServerLevel serverLevel) {
                DeviceIndex index = DeviceIndex.get(serverLevel);
                index.unregister(receiver.getDeviceId());
                UUID boundSource = receiver.getBoundSource();
                if (boundSource != null) {
                    index.unregisterReceiver(boundSource, receiver);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof ReceiverBlockEntity receiver ? receiver.getOutputLevel() : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (getConnectedDirection(state) != direction) {
            return 0;
        }
        return level.getBlockEntity(pos) instanceof ReceiverBlockEntity receiver ? receiver.getOutputLevel() : 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    private void updateNeighbors(BlockState state, Level level, BlockPos pos) {
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.relative(getConnectedDirection(state).getOpposite()), this);
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
