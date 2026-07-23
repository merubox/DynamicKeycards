package com.mbx.dynamickeycards.block;

import com.mojang.serialization.MapCodec;
import com.mbx.dynamickeycards.DKSounds;
import com.mbx.dynamickeycards.DKTooltips;
import com.mbx.dynamickeycards.DKConfig;
import com.mbx.dynamickeycards.item.BlankKeycardItem;
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
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A card reader that binds to whoever places it and only emits its redstone pulse when
 * used with a keycard registered on it. Placeable on floor, wall, or ceiling.
 *
 * <p>The front panel has four visual states ({@link CardReaderMode}): idle, green while
 * the accept pulse runs, red for {@link #DENIED_TICKS} after a rejected keycard, and the
 * register display while register mode is armed. The pulse length comes from the reader
 * (its per-block override, else {@link DKConfig#DEFAULT_PULSE_LENGTH_TICKS}). State
 * changes only start from OFF (the reader is "busy" during a pulse or denial flash), so
 * scheduled ticks can never strand the redstone output. A golden keycard bypasses registration:
 * standing use always passes, sneaking use toggles register mode like the owner's bare
 * hand would.
 */
public class CardReaderBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {

    public static final BooleanProperty PRESSED = BooleanProperty.create("pressed");
    public static final EnumProperty<CardReaderMode> MODE = EnumProperty.create("mode", CardReaderMode.class);

    /** How long the red "denied" light stays on. */
    public static final int DENIED_TICKS = 20;

    protected static final VoxelShape FLOOR_X_SHAPE = Block.box(3, 0, 5, 13, 2, 11);
    protected static final VoxelShape FLOOR_Z_SHAPE = Block.box(5, 0, 3, 11, 2, 13);
    protected static final VoxelShape CEILING_X_SHAPE = Block.box(3, 14, 5, 13, 16, 11);
    protected static final VoxelShape CEILING_Z_SHAPE = Block.box(5, 14, 3, 11, 16, 13);
    protected static final VoxelShape NORTH_SHAPE = Block.box(5, 3, 14, 11, 13, 16);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(5, 3, 0, 11, 13, 2);
    protected static final VoxelShape WEST_SHAPE = Block.box(14, 3, 5, 16, 13, 11);
    protected static final VoxelShape EAST_SHAPE = Block.box(0, 3, 5, 2, 13, 11);

    public CardReaderBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FACE, AttachFace.WALL)
                .setValue(PRESSED, false)
                .setValue(MODE, CardReaderMode.OFF));
    }

    @Override
    protected MapCodec<? extends CardReaderBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE, PRESSED, MODE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CardReaderBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof CardReaderBlockEntity reader) {
            reader.setOwner(player.getUUID());
        }
    }

    /**
     * Bare-hand interaction. With register mode active, any bare-hand click (owner or not,
     * sneaking or not) cancels it; otherwise sneaking either arms register mode (owner) or
     * reports the reader as not bound (everyone else), and standing clicks do nothing.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSpectator() || !(level.getBlockEntity(pos) instanceof CardReaderBlockEntity reader)) {
            return InteractionResult.PASS;
        }
        if (reader.isRegisterMode()) {
            if (!level.isClientSide) {
                cancelRegisterMode(level, pos, state, reader, player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (state.getValue(MODE) != CardReaderMode.OFF) {
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide) {
            if (reader.isOwner(player)) {
                armRegisterMode(level, pos, state, reader, player);
            } else {
                message(player, "not_bound", ChatFormatting.RED);
                DKSounds.deny(level, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Held-item interaction. Non-keycard items never react (and never fall through to the
     * bare-hand path). Keycards register while sneaking in register mode, pass silently
     * when standing with a registered keycard, and report as unregistered otherwise. The
     * golden keycard bypasses all of it: standing use always passes, sneaking use toggles
     * register mode exactly like the owner's bare hand.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (player.isSpectator() || !(stack.getItem() instanceof KeycardItem)
                || !(level.getBlockEntity(pos) instanceof CardReaderBlockEntity reader)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        boolean sneaking = player.isShiftKeyDown();
        // The golden keycard is a master key for every reader; an Estate keycard is one for
        // the readers owned by the player it's bound to. Both drive the same behavior.
        if (stack.getItem() instanceof GoldenKeycardItem) {
            return masterKeyInteract(state, level, pos, player, sneaking, reader);
        }
        if (stack.getItem() instanceof EstateKeycardItem) {
            UUID cardOwner = EstateKeycardItem.boundOwner(stack);
            if (cardOwner != null && cardOwner.equals(reader.getOwner())) {
                return masterKeyInteract(state, level, pos, player, sneaking, reader);
            }
            // unbound, or bound to a different owner's readers: no access here
            if (sneaking) {
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                if (state.getValue(MODE) == CardReaderMode.OFF) {
                    setMode(level, pos, state, CardReaderMode.DENIED);
                    level.scheduleTick(pos, this, DENIED_TICKS);
                }
                message(player, "unregistered_card", ChatFormatting.RED);
                DKSounds.deny(level, pos);
            }
            return ItemInteractionResult.CONSUME;
        }
        if (reader.isRegisterMode()) {
            if (!sneaking) {
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                // one press always toggles this card's access; the block list is an
                // internal detail — players only ever see "registered"/"removed"
                UUID ownKey = KeycardItem.ownKey(stack);
                if (stack.getItem() instanceof CrewMemberKeycardItem) {
                    // members are pure pass tokens bound to their manager: no registering,
                    // no per-reader toggling — all control goes through the manager card
                    message(player, "member_not_registerable", ChatFormatting.RED);
                    DKSounds.deny(level, pos);
                } else if (stack.getItem() instanceof BlankKeycardItem) {
                    // a blank card is keyed and turned into a keycard on registration
                    if (reader.getRegisteredCount() >= DKConfig.MAX_REGISTRATIONS_PER_READER.get()) {
                        message(player, "register_limit", ChatFormatting.RED);
                        DKSounds.deny(level, pos);
                    } else {
                        UUID key = UUID.randomUUID();
                        ItemStack keyed = new ItemStack(DKItems.keycardFor(stack), stack.getCount());
                        keyed.set(DKComponents.CARD_ID.get(), key);
                        player.setItemInHand(hand, keyed);
                        reader.registerCard(key);
                        message(player, "register_complete", ChatFormatting.GREEN);
                        DKSounds.confirm(level, pos);
                    }
                } else if (ownKey != null && reader.isBlocked(ownKey)) {
                    reader.unblockCard(ownKey);
                    message(player, "register_complete", ChatFormatting.GREEN);
                    DKSounds.confirm(level, pos);
                } else if (ownKey != null && reader.isRegistered(ownKey)) {
                    reader.removeCard(ownKey);
                    if (reader.isRegisteredAny(KeycardItem.inheritedKeys(stack))) {
                        reader.blockCard(ownKey);
                    }
                    message(player, "register_removed", ChatFormatting.WHITE);
                    DKSounds.remove(level, pos);
                } else if (ownKey != null && reader.isRegisteredAny(KeycardItem.inheritedKeys(stack))) {
                    // passes only through inherited keys: shut out just this card
                    reader.blockCard(ownKey);
                    message(player, "register_removed", ChatFormatting.WHITE);
                    DKSounds.remove(level, pos);
                } else if (reader.getRegisteredCount() >= DKConfig.MAX_REGISTRATIONS_PER_READER.get()) {
                    message(player, "register_limit", ChatFormatting.RED);
                    DKSounds.deny(level, pos);
                } else {
                    // keyed keycard, or a blank crew manager minting its group key
                    if (ownKey == null) {
                        ownKey = UUID.randomUUID();
                        stack.set(DKComponents.CARD_ID.get(), ownKey);
                    }
                    reader.registerCard(ownKey);
                    message(player, "register_complete", ChatFormatting.GREEN);
                    DKSounds.confirm(level, pos);
                }
                reader.setRegisterMode(false);
                setMode(level, pos, state, CardReaderMode.OFF);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (sneaking) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        UUID ownKey = KeycardItem.ownKey(stack);
        boolean blocked = ownKey != null && reader.isBlocked(ownKey);
        if (!blocked && ownKey != null && reader.isRegisteredAny(KeycardItem.allKeys(stack))) {
            if (state.getValue(MODE) != CardReaderMode.OFF) {
                return ItemInteractionResult.CONSUME;
            }
            this.acceptPulse(state, level, pos, player);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            if (state.getValue(MODE) == CardReaderMode.OFF) {
                setMode(level, pos, state, CardReaderMode.DENIED);
                level.scheduleTick(pos, this, DENIED_TICKS);
            }
            message(player, "unregistered_card", ChatFormatting.RED);
            DKSounds.deny(level, pos);
        }
        return ItemInteractionResult.CONSUME;
    }

    /**
     * Master-key behavior shared by the golden keycard and a matching Estate keycard: in
     * register mode, sneaking asks to confirm / performs the full reset and standing cancels;
     * otherwise sneaking arms register mode and standing pulses the reader open.
     */
    private ItemInteractionResult masterKeyInteract(BlockState state, Level level, BlockPos pos,
                                                    Player player, boolean sneaking, CardReaderBlockEntity reader) {
        if (reader.isRegisterMode()) {
            if (!level.isClientSide) {
                if (sneaking) {
                    if (reader.isResetPending()) {
                        // confirmed: wipe every registered card
                        reader.clearCards();
                        reader.setRegisterMode(false);
                        setMode(level, pos, state, CardReaderMode.OFF);
                        message(player, "reset_complete", ChatFormatting.WHITE);
                        DKSounds.remove(level, pos);
                    } else {
                        // a full reset is destructive — ask for a confirming second click
                        reader.setResetPending(true);
                        message(player, "reset_confirm", ChatFormatting.RED);
                        DKSounds.deny(level, pos);
                    }
                } else {
                    cancelRegisterMode(level, pos, state, reader, player);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (state.getValue(MODE) != CardReaderMode.OFF) {
            return ItemInteractionResult.CONSUME;
        }
        if (sneaking) {
            if (!level.isClientSide) {
                armRegisterMode(level, pos, state, reader, player);
            }
        } else {
            this.acceptPulse(state, level, pos, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private void acceptPulse(BlockState state, Level level, BlockPos pos, Player player) {
        int pulseTicks = level.getBlockEntity(pos) instanceof CardReaderBlockEntity reader
                ? reader.getPulseLength()
                : DKConfig.DEFAULT_PULSE_LENGTH_TICKS.get();
        level.setBlock(pos, state.setValue(MODE, CardReaderMode.ACCEPTED).setValue(PRESSED, true), Block.UPDATE_ALL);
        this.updateNeighbors(state, level, pos);
        level.scheduleTick(pos, this, pulseTicks);
        level.playSound(player, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.3f, 0.6f);
        if (!level.isClientSide) {
            DKSounds.accept(level, pos);
        }
        level.gameEvent(player, GameEvent.BLOCK_ACTIVATE, pos);
    }

    private void armRegisterMode(Level level, BlockPos pos, BlockState state, CardReaderBlockEntity reader, Player player) {
        reader.setRegisterMode(true);
        setMode(level, pos, state, CardReaderMode.REGISTER);
        message(player, "register_prompt", ChatFormatting.WHITE);
        DKSounds.arm(level, pos);
    }

    private void cancelRegisterMode(Level level, BlockPos pos, BlockState state, CardReaderBlockEntity reader, Player player) {
        reader.setRegisterMode(false);
        setMode(level, pos, state, CardReaderMode.OFF);
        message(player, "register_cancelled", ChatFormatting.WHITE);
        DKSounds.remove(level, pos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        switch (state.getValue(MODE)) {
            case ACCEPTED -> {
                level.setBlock(pos, state.setValue(MODE, CardReaderMode.OFF).setValue(PRESSED, false), Block.UPDATE_ALL);
                this.updateNeighbors(state, level, pos);
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.3f, 0.5f);
                level.gameEvent(null, GameEvent.BLOCK_DEACTIVATE, pos);
            }
            case DENIED -> setMode(level, pos, state, CardReaderMode.OFF);
            default -> { }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (moved || state.is(newState.getBlock())) {
            return;
        }
        if (state.getValue(PRESSED)) {
            this.updateNeighbors(state, level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(PRESSED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (state.getValue(PRESSED) && getConnectedDirection(state) == direction) {
            return 15;
        }
        return 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.DESTROY;
    }

    private void updateNeighbors(BlockState state, Level level, BlockPos pos) {
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.relative(getConnectedDirection(state).getOpposite()), this);
    }

    private static void setMode(Level level, BlockPos pos, BlockState state, CardReaderMode mode) {
        level.setBlock(pos, state.setValue(MODE, mode), Block.UPDATE_ALL);
    }

    private static void message(Player player, String key, ChatFormatting color) {
        player.displayClientMessage(Component.translatable("dynamickeycards.card_reader." + key).withStyle(color), true);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction direction = state.getValue(FACING);
        switch (state.getValue(FACE)) {
            case FLOOR -> {
                if (direction.getAxis() == Direction.Axis.X) {
                    return FLOOR_X_SHAPE;
                }
                return FLOOR_Z_SHAPE;
            }
            case WALL -> {
                switch (direction) {
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
                if (direction.getAxis() == Direction.Axis.X) {
                    return CEILING_X_SHAPE;
                }
                return CEILING_Z_SHAPE;
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
        DKTooltips.summary(tooltip, "card_reader1", "card_reader2", "card_reader3");
    }
}
