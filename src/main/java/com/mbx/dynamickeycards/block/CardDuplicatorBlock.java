package com.mbx.dynamickeycards.block;

import com.mojang.serialization.MapCodec;
import com.mbx.dynamickeycards.DKSounds;
import com.mbx.dynamickeycards.DKTooltips;
import com.mbx.dynamickeycards.item.BlankKeycardItem;
import com.mbx.dynamickeycards.item.CrewManagerKeycardItem;
import com.mbx.dynamickeycards.item.CrewMemberKeycardItem;
import com.mbx.dynamickeycards.item.EstateKeycardItem;
import com.mbx.dynamickeycards.item.GoldenKeycardItem;
import com.mbx.dynamickeycards.item.KeycardItem;
import com.mbx.dynamickeycards.registry.DKComponents;
import com.mbx.dynamickeycards.registry.DKItems;
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

import java.util.List;
import java.util.UUID;


/**
 * Forks a keycard onto blank keycards: source and copy keep every key shared so far,
 * then each side carries a fresh own key, so later registrations never propagate
 * between them. All interaction is sneak-right-click:
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
        if (duplicator.getSourceKeys() != null) {
            if (!level.isClientSide) {
                duplicator.setSourceKeys(null);
                setMode(level, pos, state, DuplicatorMode.IDLE);
                message(player, "cancelled", ChatFormatting.WHITE);
                DKSounds.remove(level, pos);
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
            } else if (stack.getItem() instanceof EstateKeycardItem) {
                this.deny(state, level, pos, player, "estate");
            } else if (duplicator.getSourceKeys() == null) {
                if (stack.getItem() instanceof CrewMemberKeycardItem) {
                    this.deny(state, level, pos, player, "member_source");
                } else if (KeycardItem.ownKey(stack) == null) {
                    this.deny(state, level, pos, player, "blank_source");
                } else if (stack.getItem() instanceof CrewManagerKeycardItem) {
                    // the manager is never re-keyed: issued cards must follow its future
                    // registrations, so the group key stays the manager's own key
                    duplicator.setSourceKeys(KeycardItem.allKeys(stack), true);
                    setMode(level, pos, state, DuplicatorMode.ARMED);
                    level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.3f, 0.8f);
                    DKSounds.arm(level, pos);
                    message(player, "target_prompt", ChatFormatting.WHITE);
                } else {
                    // snapshot fork: freeze the keys both cards will share, then give the
                    // source a fresh own key so future registrations no longer propagate
                    duplicator.setSourceKeys(KeycardItem.allKeys(stack));
                    KeycardItem.rekey(stack);
                    setMode(level, pos, state, DuplicatorMode.ARMED);
                    level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.3f, 0.8f);
                    DKSounds.arm(level, pos);
                    message(player, "target_prompt", ChatFormatting.WHITE);
                }
            } else {
                if (stack.getItem() instanceof CrewManagerKeycardItem && KeycardItem.ownKey(stack) == null) {
                    // blank manager target → co-manager (source must be a manager)
                    if (!duplicator.isSourceManager()) {
                        this.deny(state, level, pos, player, "manager_target");
                    } else {
                        // an exact clone of the group key (the manager's own key = last frozen key)
                        List<UUID> keys = duplicator.getSourceKeys();
                        stack.set(DKComponents.CARD_ID.get(), keys.get(keys.size() - 1));
                        this.complete(state, level, pos, player, duplicator);
                    }
                } else if (!(stack.getItem() instanceof BlankKeycardItem)) {
                    this.deny(state, level, pos, player, "not_blank");
                } else if (duplicator.isSourceManager()) {
                    // issue crew members: same-color member cards carrying the group keys
                    ItemStack members = new ItemStack(DKItems.memberCardFor(stack), stack.getCount());
                    KeycardItem.inheritFrom(members, duplicator.getSourceKeys());
                    player.setItemInHand(hand, members);
                    this.complete(state, level, pos, player, duplicator);
                } else {
                    // fork copy: a blank card becomes a keyed keycard inheriting the frozen keys
                    ItemStack keyed = new ItemStack(DKItems.keycardFor(stack), stack.getCount());
                    KeycardItem.inheritFrom(keyed, duplicator.getSourceKeys());
                    player.setItemInHand(hand, keyed);
                    this.complete(state, level, pos, player, duplicator);
                }
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private void complete(BlockState state, Level level, BlockPos pos, Player player, CardDuplicatorBlockEntity duplicator) {
        duplicator.setSourceKeys(null);
        setMode(level, pos, state, DuplicatorMode.COMPLETE);
        level.scheduleTick(pos, this, COMPLETE_TICKS);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.3f, 1.2f);
        DKSounds.accept(level, pos);
        message(player, "complete", ChatFormatting.GREEN);
    }

    private void deny(BlockState state, Level level, BlockPos pos, Player player, String key) {
        setMode(level, pos, state, DuplicatorMode.DENIED);
        level.scheduleTick(pos, this, DENIED_TICKS);
        DKSounds.deny(level, pos);
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
                    && duplicator.getSourceKeys() != null;
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
