package com.mbx.dynamickeycards.block;

import com.mojang.serialization.MapCodec;
import com.mbx.dynamickeycards.DKTooltips;
import com.mbx.dynamickeycards.item.GoldenKeycardItem;
import com.mbx.dynamickeycards.item.KeycardItem;
import com.mbx.dynamickeycards.registry.DKComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Copies a keycard's key onto blank keycards. All interaction is sneak-right-click:
 * insert the keyed source card (green light blinks), then a blank card to complete the
 * copy (solid green). Bare-hand sneaking prompts, or cancels an in-progress copy.
 * Rejected inputs (golden keycards, blank sources, non-blank targets) flash red for
 * {@link #DENIED_TICKS}; a pending source survives the flash. Emits no redstone.
 */
public class CardDuplicatorBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {

    public static final EnumProperty<DuplicatorMode> MODE = EnumProperty.create("mode", DuplicatorMode.class);

    /** How long the solid green "complete" light stays on. */
    public static final int COMPLETE_TICKS = 30;
    /** How long the red "denied" light stays on. */
    public static final int DENIED_TICKS = 20;

    protected static final VoxelShape FLOOR_SHAPE = Block.box(4, 0, 4, 12, 2, 12);
    protected static final VoxelShape CEILING_SHAPE = Block.box(4, 14, 4, 12, 16, 12);
    protected static final VoxelShape NORTH_SHAPE = Block.box(4, 4, 14, 12, 12, 16);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(4, 4, 0, 12, 12, 2);
    protected static final VoxelShape WEST_SHAPE = Block.box(14, 4, 4, 16, 12, 12);
    protected static final VoxelShape EAST_SHAPE = Block.box(0, 4, 4, 2, 12, 12);

    public CardDuplicatorBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FACE, AttachFace.FLOOR)
                .setValue(MODE, DuplicatorMode.IDLE));
    }

    @Override
    protected MapCodec<? extends CardDuplicatorBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE, MODE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CardDuplicatorBlockEntity(pos, state);
    }

    /**
     * Bare hand. Like the card reader's register mode, a pending copy is cancelled by any
     * bare-hand click, standing or sneaking; otherwise sneaking shows the source prompt.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSpectator() || !(level.getBlockEntity(pos) instanceof CardDuplicatorBlockEntity duplicator)) {
            return InteractionResult.PASS;
        }
        if (duplicator.getSourceKey() != null) {
            if (!level.isClientSide) {
                duplicator.setSourceKey(null);
                setMode(level, pos, state, DuplicatorMode.IDLE);
                message(player, "cancelled", ChatFormatting.WHITE);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            message(player, "source_prompt", ChatFormatting.WHITE);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (player.isSpectator() || !player.isShiftKeyDown() || !(stack.getItem() instanceof KeycardItem)
                || !(level.getBlockEntity(pos) instanceof CardDuplicatorBlockEntity duplicator)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        DuplicatorMode mode = state.getValue(MODE);
        if (mode == DuplicatorMode.COMPLETE || mode == DuplicatorMode.DENIED) {
            return ItemInteractionResult.CONSUME;
        }
        if (!level.isClientSide) {
            if (stack.getItem() instanceof GoldenKeycardItem) {
                this.deny(state, level, pos, player, "golden");
            } else if (duplicator.getSourceKey() == null) {
                UUID key = stack.get(DKComponents.CARD_ID.get());
                if (key == null) {
                    this.deny(state, level, pos, player, "blank_source");
                } else {
                    duplicator.setSourceKey(key);
                    setMode(level, pos, state, DuplicatorMode.ARMED);
                    level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.3f, 0.8f);
                    message(player, "target_prompt", ChatFormatting.WHITE);
                }
            } else {
                if (stack.get(DKComponents.CARD_ID.get()) != null) {
                    this.deny(state, level, pos, player, "not_blank");
                } else {
                    stack.set(DKComponents.CARD_ID.get(), duplicator.getSourceKey());
                    duplicator.setSourceKey(null);
                    setMode(level, pos, state, DuplicatorMode.COMPLETE);
                    level.scheduleTick(pos, this, COMPLETE_TICKS);
                    level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.3f, 1.2f);
                    message(player, "complete", ChatFormatting.GREEN);
                }
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private void deny(BlockState state, Level level, BlockPos pos, Player player, String key) {
        setMode(level, pos, state, DuplicatorMode.DENIED);
        level.scheduleTick(pos, this, DENIED_TICKS);
        message(player, key, ChatFormatting.RED);
    }

    /** Timed lights fall back: complete to idle, denied back to armed if a source is pending. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        DuplicatorMode mode = state.getValue(MODE);
        if (mode == DuplicatorMode.COMPLETE) {
            setMode(level, pos, state, DuplicatorMode.IDLE);
        } else if (mode == DuplicatorMode.DENIED) {
            boolean armed = level.getBlockEntity(pos) instanceof CardDuplicatorBlockEntity duplicator
                    && duplicator.getSourceKey() != null;
            setMode(level, pos, state, armed ? DuplicatorMode.ARMED : DuplicatorMode.IDLE);
        }
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.DESTROY;
    }

    private static void setMode(Level level, BlockPos pos, BlockState state, DuplicatorMode mode) {
        level.setBlock(pos, state.setValue(MODE, mode), Block.UPDATE_ALL);
    }

    private static void message(Player player, String key, ChatFormatting color) {
        player.displayClientMessage(Component.translatable("dynamickeycards.card_duplicator." + key).withStyle(color), true);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        switch (state.getValue(FACE)) {
            case FLOOR -> {
                return FLOOR_SHAPE;
            }
            case WALL -> {
                switch (state.getValue(FACING)) {
                    case EAST -> {
                        return EAST_SHAPE;
                    }
                    case WEST -> {
                        return WEST_SHAPE;
                    }
                    case SOUTH -> {
                        return SOUTH_SHAPE;
                    }
                    default -> {
                        return NORTH_SHAPE;
                    }
                }
            }
            default -> {
                return CEILING_SHAPE;
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
        DKTooltips.summary(tooltip, "card_duplicator1", "card_duplicator2");
    }
}
