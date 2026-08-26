package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DKNetwork;

import com.mojang.serialization.MapCodec;
import com.mbx.dynamickeycards.DKSounds;
import com.mbx.dynamickeycards.DKTooltips;
import com.mbx.dynamickeycards.DKConfig;
import com.mbx.dynamickeycards.item.BlankKeycardItem;
import com.mbx.dynamickeycards.item.BoundSensorBlockItem;
import com.mbx.dynamickeycards.item.CrewMemberKeycardItem;
import com.mbx.dynamickeycards.item.EstateKeycardItem;
import com.mbx.dynamickeycards.item.GoldenKeycardItem;
import com.mbx.dynamickeycards.item.KeycardItem;
import com.mbx.dynamickeycards.item.LinkedReaderBlockItem;
import com.mbx.dynamickeycards.item.ReceiverBlockItem;
import com.mbx.dynamickeycards.registry.DKBlockEntities;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
import net.neoforged.neoforge.common.Tags;
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
public class CardReaderBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock, WrenchConfigurableBlock {

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
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof CardReaderBlockEntity reader)) {
            return;
        }
        if (placer instanceof Player player) {
            reader.setOwner(player.getUUID());
        }
        // if this reader item was set to link with an existing one (see LinkedReaderBlockItem),
        // point both readers at each other now that this one actually exists in the world - adds
        // to each reader's own set of links rather than replacing it, so linking a third reader
        // to an already-linked one extends the group instead of severing its existing link
        UUID linkTargetId = LinkedReaderBlockItem.linkedReader(stack);
        BlockPos linkTargetPos = linkTargetId != null && level instanceof ServerLevel serverLevel
                ? DeviceIndex.get(serverLevel).getPosition(linkTargetId) : null;
        if (linkTargetPos != null && level.getBlockEntity(linkTargetPos) instanceof CardReaderBlockEntity target) {
            reader.addLinkedReader(target.getDeviceId());
            target.addLinkedReader(reader.getDeviceId());
            if (placer instanceof Player player) {
                // green: unlike the white "tuned" message shown when the item was set to link,
                // this is the point the link actually exists
                player.displayClientMessage(
                        Component.translatable("dynamickeycards.link_device.linked").withStyle(ChatFormatting.GREEN), true);
                DKSounds.confirm(level, pos);
            }
        }
    }

    /**
     * Bare-hand interaction. With register mode active, any bare-hand click (owner or not,
     * sneaking or not) cancels it; otherwise sneaking either arms register mode (owner) or
     * reports the reader as not bound (everyone else), and standing clicks do nothing. The owner
     * can arm register mode regardless of what the reader's doing right now - including cutting
     * an in-progress accept signal short (see {@link #armRegisterMode}) - rather than having to
     * wait out an active signal or a denied flash first.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSpectator() || !(level.getBlockEntity(pos) instanceof CardReaderBlockEntity reader)) {
            return InteractionResult.PASS;
        }
        if (reader.isRegisterMode()) {
            // sneaking exits register mode - same sneak-only toggle gesture as everywhere else
            // (see the class doc). Standing performs a reset, same as masterKeyInteract's own
            // reset flow, but only for the owner - matches the class doc's "리더 자체가 해당
            // 플레이어의 것일 때" exception (a bare hand can't fully stand in for a keycard, but
            // it can for anything the owner themself is doing).
            if (player.isShiftKeyDown()) {
                if (!level.isClientSide) {
                    cancelRegisterMode(level, pos, state, reader, player);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!reader.isOwner(player)) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                if (reader.isResetPending()) {
                    reader.clearCards();
                    reader.setRegisterMode(false);
                    setMode(level, pos, state, CardReaderMode.OFF);
                    message(player, "reset_complete", ChatFormatting.WHITE);
                    DKSounds.remove(level, pos);
                } else {
                    reader.armResetPending();
                    message(player, "reset_confirm", ChatFormatting.RED);
                    DKSounds.deny(level, pos);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
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
        if (stack.is(Tags.Items.TOOLS_WRENCH) || MaintenanceAccess.isMaintenanceCard(stack)) {
            return maintenanceInteract(stack, state, level, pos, player);
        }
        // The three unplaced-device items are handled here rather than in each item's own useOn,
        // so they aren't swallowed by the SKIP_DEFAULT_BLOCK_INTERACTION fallback below.
        if (stack.getItem() instanceof BoundSensorBlockItem sensorItem) {
            return bindSensorItem(sensorItem, stack, state, level, pos, player);
        }
        if (stack.getItem() instanceof LinkedReaderBlockItem readerItem) {
            return linkReaderItem(readerItem, stack, state, level, pos, player);
        }
        if (stack.getItem() instanceof ReceiverBlockItem) {
            return bindReceiverItem(stack, state, level, pos, player);
        }
        if (player.isSpectator() || !(stack.getItem() instanceof KeycardItem)
                || !(level.getBlockEntity(pos) instanceof CardReaderBlockEntity reader)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return keycardInteract(stack, state, level, pos, player, hand, reader);
    }

    /**
     * A wrench-tagged item (any mod's, not gated on Create) or either maintenance card. Standing
     * opens the config UI, sneaking picks the reader up - both gated by {@link MaintenanceAccess}
     * (the owner always has both; anyone else needs a golden or matching estate maintenance card).
     */
    private ItemInteractionResult maintenanceInteract(ItemStack stack, BlockState state, Level level,
                                                      BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof CardReaderBlockEntity reader)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!MaintenanceAccess.hasAccess(player, stack, reader.getOwner())) {
            if (!level.isClientSide) {
                message(player, "not_bound", ChatFormatting.RED);
                DKSounds.deny(level, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player.isShiftKeyDown()) {
            return wrenchPickup(state, level, pos, player, reader);
        }
        return openLinkDeviceMenu(state, level, pos, player, reader);
    }

    /** Tunes an unplaced advanced sensor to this reader, so placing it completes the link. */
    private ItemInteractionResult bindSensorItem(BoundSensorBlockItem sensorItem, ItemStack stack, BlockState state,
                                                 Level level, BlockPos pos, Player player) {
        CardReaderBlockEntity reader = armedReaderOrDeny(level, pos, player);
        if (reader == null) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            sensorItem.bindTo(stack, reader.getDeviceId());
            syncBoundItemToClient(player, reader, pos);
            consumeArmingAndAnnounceTuned(level, pos, state, player, reader);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Tunes an unplaced reader to link with this one. Linking only shares registered/blocked cards
     * (see {@link CardReaderBlockEntity#accepts}) - each reader keeps its own owner, mode/frequency,
     * and pulse.
     */
    private ItemInteractionResult linkReaderItem(LinkedReaderBlockItem readerItem, ItemStack stack, BlockState state,
                                                 Level level, BlockPos pos, Player player) {
        CardReaderBlockEntity reader = armedReaderOrDeny(level, pos, player);
        if (reader == null) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            readerItem.linkTo(stack, reader.getDeviceId());
            syncBoundItemToClient(player, reader, pos);
            consumeArmingAndAnnounceTuned(level, pos, state, player, reader);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Binds an unplaced receiver to this reader as its wireless source. */
    private ItemInteractionResult bindReceiverItem(ItemStack stack, BlockState state, Level level,
                                                   BlockPos pos, Player player) {
        CardReaderBlockEntity reader = armedReaderOrDeny(level, pos, player);
        if (reader == null) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            reader.setRegisterMode(false);
            setMode(level, pos, state, CardReaderMode.OFF);
        }
        return SignalSource.tryBindReceiverItem(stack, level, pos, player, reader);
    }

    /**
     * Pushes the freshly-tuned stack and this reader's position to the client immediately, rather
     * than waiting for the next automatic per-tick sync. Without it the client can spend a visible
     * stretch still holding its pre-bind copy, which is what kept the bind-target highlight from
     * lighting up at the moment of binding.
     */
    private static void syncBoundItemToClient(Player player, CardReaderBlockEntity reader, BlockPos pos) {
        player.containerMenu.broadcastChanges();
        DKNetwork.registerDevicePosition(player, reader.getDeviceId(), pos);
    }

    /**
     * Everything a {@link KeycardItem} does at a reader: the golden and matching estate cards act
     * as master keys, register mode toggles the card's access, and otherwise the card either passes
     * or is denied.
     */
    private ItemInteractionResult keycardInteract(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                  Player player, InteractionHand hand, CardReaderBlockEntity reader) {
        boolean sneaking = player.isShiftKeyDown();
        // The golden keycard is a master key for every reader; an Estate keycard is one for the
        // readers owned by the player it's bound to. Both drive the same behavior.
        if (stack.getItem() instanceof GoldenKeycardItem) {
            return masterKeyInteract(state, level, pos, player, sneaking, reader);
        }
        if (stack.getItem() instanceof EstateKeycardItem) {
            return estateKeycardInteract(stack, state, level, pos, player, sneaking, reader);
        }
        if (reader.isRegisterMode()) {
            if (!level.isClientSide) {
                toggleCardRegistration(stack, state, level, pos, player, hand, reader);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (sneaking) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        // accepts() also honors a linked reader's own registrations/blocks - see CardReaderBlockEntity#accepts
        if (reader.accepts(stack)) {
            if (state.getValue(MODE) != CardReaderMode.OFF) {
                return ItemInteractionResult.CONSUME;
            }
            this.acceptPulse(state, level, pos, player);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return denyUnregisteredCard(state, level, pos, player);
    }

    /** A matching estate card is a master key here; an unbound one, or one bound elsewhere, is just denied. */
    private ItemInteractionResult estateKeycardInteract(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                        Player player, boolean sneaking, CardReaderBlockEntity reader) {
        UUID cardOwner = EstateKeycardItem.boundOwner(stack);
        if (cardOwner != null && cardOwner.equals(reader.getOwner())) {
            return masterKeyInteract(state, level, pos, player, sneaking, reader);
        }
        if (sneaking) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return denyUnregisteredCard(state, level, pos, player);
    }

    /**
     * One press in register mode always toggles this card's access; the block list is an internal
     * detail, so players only ever see "registered"/"removed". Sneaking isn't required - only the
     * register-mode on/off toggle itself needs it (see this class's doc for the
     * "등록모드 켜고 끄기만 웅크림 필수" gesture principle).
     *
     * <p>Server-side only; the caller checks that. Spends the arming either way, like every other
     * register-mode action.
     */
    private void toggleCardRegistration(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                        Player player, InteractionHand hand, CardReaderBlockEntity reader) {
        UUID ownKey = KeycardItem.ownKey(stack);
        if (stack.getItem() instanceof CrewMemberKeycardItem) {
            // members are pure pass tokens bound to their manager: no registering, no per-reader
            // toggling - all control goes through the manager card
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
        } else if (ownKey != null && reader.isBlockedInGroup(ownKey)) {
            reader.unblockInGroup(ownKey);
            message(player, "register_complete", ChatFormatting.GREEN);
            DKSounds.confirm(level, pos);
        } else if (ownKey != null && reader.isRegisteredInGroup(ownKey)) {
            // wherever in the linked group this card actually lives, not just here - see
            // CardReaderBlockEntity#isRegisteredInGroup for the bug this avoids
            reader.removeRegisteredFromGroup(ownKey);
            if (reader.isRegisteredAnyInGroup(KeycardItem.inheritedKeys(stack))) {
                reader.blockCard(ownKey);
            }
            message(player, "register_removed", ChatFormatting.WHITE);
            DKSounds.remove(level, pos);
        } else if (ownKey != null && reader.isRegisteredAnyInGroup(KeycardItem.inheritedKeys(stack))) {
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

    /**
     * The reader at {@code pos} if it is currently in register mode, or {@code null} after telling
     * the player why not. Binding a sensor, reader, or receiver item all go through this: register
     * mode is the owner's consent, since only the owner can arm it (see {@link #armRegisterMode}).
     * Without that gate anyone could quietly tune their own device to someone else's reader.
     */
    @Nullable
    private CardReaderBlockEntity armedReaderOrDeny(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof CardReaderBlockEntity reader && reader.isRegisterMode()) {
            return reader;
        }
        if (!level.isClientSide) {
            player.displayClientMessage(
                    Component.translatable("dynamickeycards.link_device.needs_register_mode").withStyle(ChatFormatting.RED), true);
            DKSounds.deny(level, pos);
        }
        return null;
    }

    /**
     * Spends the one tuning that arming register mode granted, then reports it. One arming grants
     * exactly one tuning, same as one card registration - otherwise leaving register mode armed
     * (nothing times it out) would let every stranger who walks up tune their own device to this
     * reader, one after another.
     *
     * <p>White, not green: this only tunes the held item. The connection isn't "complete" (green)
     * until it's placed - see {@code AdvancedSensorBlockEntity#announcePlaced} - and no sound
     * plays here either, only the completed connection gets one.
     */
    private void consumeArmingAndAnnounceTuned(Level level, BlockPos pos, BlockState state,
                                               Player player, CardReaderBlockEntity reader) {
        reader.setRegisterMode(false);
        setMode(level, pos, state, CardReaderMode.OFF);
        player.displayClientMessage(
                Component.translatable("dynamickeycards.link_device.tuned").withStyle(ChatFormatting.WHITE), true);
    }

    /** The shared "this card doesn't open this reader" response: red light for {@link #DENIED_TICKS}, message, deny sound. */
    private ItemInteractionResult denyUnregisteredCard(BlockState state, Level level, BlockPos pos, Player player) {
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
     * register mode, sneaking cancels register mode and standing asks to confirm / performs the
     * full reset; otherwise sneaking arms register mode and standing pulses the reader open.
     * (The register-mode pair reads "backwards" on purpose - toggling register mode off is the
     * one gesture that stays sneak-gated, so the reset had to move to standing.)
     */
    private ItemInteractionResult masterKeyInteract(BlockState state, Level level, BlockPos pos,
                                                    Player player, boolean sneaking, CardReaderBlockEntity reader) {
        if (reader.isRegisterMode()) {
            // sneaking exits register mode - the on/off toggle is the one gesture that stays
            // sneak-gated (see the class doc); standing performs the reset instead, so it (like
            // every other in-register-mode action) no longer needs sneaking.
            if (!level.isClientSide) {
                if (sneaking) {
                    cancelRegisterMode(level, pos, state, reader, player);
                } else if (reader.isResetPending()) {
                    // confirmed: wipe every registered card
                    reader.clearCards();
                    reader.setRegisterMode(false);
                    setMode(level, pos, state, CardReaderMode.OFF);
                    message(player, "reset_complete", ChatFormatting.WHITE);
                    DKSounds.remove(level, pos);
                } else {
                    // a full reset is destructive — ask for a confirming second click
                    reader.armResetPending();
                    message(player, "reset_confirm", ChatFormatting.RED);
                    DKSounds.deny(level, pos);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // sneaking arms register mode regardless of what's happening right now - armRegisterMode
        // itself cuts an in-progress accept signal short rather than making the owner wait it
        // out. Standing still only passes while idle - re-tapping mid-signal or mid-denial stays
        // a no-op, same as ever.
        if (sneaking) {
            if (!level.isClientSide) {
                armRegisterMode(level, pos, state, reader, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (state.getValue(MODE) != CardReaderMode.OFF) {
            return ItemInteractionResult.CONSUME;
        }
        this.acceptPulse(state, level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * The accept pulse's "turn off" deliberately avoids {@link Level#scheduleTick}: a level
     * keeps only one pending scheduled tick per (position, block) pair and silently drops a
     * second one, so a mid-pulse length change could never re-schedule against the tick queued
     * when the pulse started. {@link #tickPulseTimeout} compares elapsed time against the
     * reader's current pulse length every game tick instead, so a length change takes effect on
     * the very next tick, whichever direction it moves.
     *
     * <p>Package-visible (not private) so {@code AdvancedSensorBlockEntity} can trigger the
     * exact same accept - sound, visuals, redstone, everything - as an ambient card-possession
     * detection instead of a physical tap. {@code player} is only used for the locally-sourced
     * click sound and the {@code GameEvent} listener position, both of which tolerate {@code null}
     * (a level-wide, unattributed sound/event) same as {@link #tickPulseTimeout}'s release already does.
     */
    void acceptPulse(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        acceptPulse(state, level, pos, player, false);
    }

    /**
     * {@link #acceptPulse(BlockState, Level, BlockPos, Player)}, but also recording whether this
     * rising edge came from an external driver (a bound advanced sensor) rather than a direct
     * tap - see {@link CardReaderBlockEntity#isPulseExternallyOriginated} for why
     * {@link #tickPulseTimeout} cares.
     */
    void acceptPulse(BlockState state, Level level, BlockPos pos, @Nullable Player player, boolean externallyOriginated) {
        CardReaderBlockEntity reader = level.getBlockEntity(pos) instanceof CardReaderBlockEntity r ? r : null;
        level.setBlock(pos, state.setValue(MODE, CardReaderMode.ACCEPTED).setValue(PRESSED, true), Block.UPDATE_ALL);
        this.updateNeighbors(state, level, pos);
        level.playSound(player, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.3f, 0.6f);
        if (!level.isClientSide) {
            DKSounds.accept(level, pos);
        }
        if (reader != null) {
            reader.onPulseStarted(externallyOriginated);
            // mirrors the accept pulse onto Create's Redstone Link network, if any
            reader.notifyLinkChanged();
            // and onto this mod's own wireless system - see CardReaderBlockEntity#getSignalSourceStrength
            reader.publishSignalSource(level);
        }
        level.gameEvent(player, GameEvent.BLOCK_ACTIVATE, pos);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != DKBlockEntities.CARD_READER.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof CardReaderBlockEntity reader) {
                tickPulseTimeout(lvl, pos, st, reader);
                reader.tickSignalSourcePublish(lvl);
            }
        };
    }

    /**
     * Ends the accept pulse once it's no longer wanted - the only place that ever calls
     * {@link #releasePulse}, so there's exactly one authority deciding when a pulse actually
     * ends (a driving sensor only ever extends {@link CardReaderBlockEntity#holdExternalSignal}'s
     * deadline, see {@code AdvancedSensorBlockEntity#tick}; it never releases the pulse itself).
     * While a bound advanced sensor is still actively holding this pulse open (see
     * {@link CardReaderBlockEntity#isExternallyHeld}), this reader's own timing is skipped
     * entirely. Once that hold ends, what happens next depends on how this pulse started
     * ({@link CardReaderBlockEntity#isPulseExternallyOriginated}): a sensor-started pulse
     * releases immediately - the sensor's own hold delay already decided how long it should run,
     * there's nothing left to wait on - while a directly-tapped one falls back to this reader's
     * own configured length as always, even if a sensor happened to extend it along the way.
     */
    private void tickPulseTimeout(Level level, BlockPos pos, BlockState state, CardReaderBlockEntity reader) {
        if (state.getValue(MODE) != CardReaderMode.ACCEPTED) {
            return;
        }
        long now = level.getGameTime();
        if (reader.isExternallyHeld(now)) {
            return;
        }
        if (reader.isPulseExternallyOriginated()) {
            releasePulse(state, level, pos);
            return;
        }
        long elapsed = now - reader.getPulseStartGameTime();
        if (elapsed < reader.getSignalLength()) {
            return;
        }
        releasePulse(state, level, pos);
    }

    /**
     * The turn-off half of an accept pulse - same side effects as {@link #tickPulseTimeout}'s
     * own release, factored out so a bound advanced sensor can force it immediately using its
     * own hold-delay instead of waiting on the reader's. Safe to call on a reader that's already
     * off (no-op via the {@code MODE != ACCEPTED} guard callers are expected to do themselves;
     * this method itself doesn't re-check, since both current callers already know it's accepted).
     */
    void releasePulse(BlockState state, Level level, BlockPos pos) {
        CardReaderBlockEntity reader = level.getBlockEntity(pos) instanceof CardReaderBlockEntity r ? r : null;
        level.setBlock(pos, state.setValue(MODE, CardReaderMode.OFF).setValue(PRESSED, false), Block.UPDATE_ALL);
        this.updateNeighbors(state, level, pos);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.3f, 0.5f);
        level.gameEvent(null, GameEvent.BLOCK_DEACTIVATE, pos);
        if (reader != null) {
            reader.notifyLinkChanged();
            // see CardReaderBlockEntity#getSignalSourceStrength - the accept pulse ending is a
            // MODE transition too, so the wireless broadcast needs its own push here just like
            // acceptPulse's rising-edge one, not just whatever the next incidental publish catches
            reader.publishSignalSource(level);
        }
    }

    /**
     * Arms register mode regardless of what the reader is currently doing - if an accept signal
     * is actively running ({@link CardReaderMode#ACCEPTED}), it's cut short first ({@link #releasePulse}
     * itself, same as it ending on its own: click-off sound, redstone drop, everything), rather
     * than making the owner wait it out before they can start managing cards. A denied flash
     * needs no such handling - it never carries a live signal to begin with (see
     * {@link #tickPulseTimeout}) - so it's overwritten the same way {@link CardReaderMode#OFF} is.
     */
    private void armRegisterMode(Level level, BlockPos pos, BlockState state, CardReaderBlockEntity reader, Player player) {
        if (state.getValue(MODE) == CardReaderMode.ACCEPTED) {
            releasePulse(state, level, pos);
            state = level.getBlockState(pos);
        }
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

    /** Only the DENIED flash still uses a plain scheduled tick - its duration never changes mid-flight. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(MODE) == CardReaderMode.DENIED) {
            setMode(level, pos, state, CardReaderMode.OFF);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (moved || state.is(newState.getBlock())) {
            return;
        }
        // tell every reader this one was linked to that the link is gone - otherwise a stale
        // id lingers in their own set (harmless on its own, since a fresh reader always gets a
        // fresh id and could never be mistaken for this one, but still worth keeping tidy for
        // anything that reads getLinkedReaders() directly)
        if (level.getBlockEntity(pos) instanceof CardReaderBlockEntity reader) {
            if (level.isClientSide) {
                ClientDeviceCache.unregister(reader.getDeviceId());
            } else if (level instanceof ServerLevel serverLevel) {
                DeviceIndex index = DeviceIndex.get(serverLevel);
                index.unregister(reader.getDeviceId());
                for (UUID linkedId : reader.getLinkedReaders()) {
                    BlockPos linkedPos = index.getPosition(linkedId);
                    if (linkedPos != null && level.getBlockEntity(linkedPos) instanceof CardReaderBlockEntity linked) {
                        linked.removeLinkedReader(reader.getDeviceId());
                    }
                }
            }
        }
        if (state.getValue(PRESSED)) {
            this.updateNeighbors(state, level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!state.getValue(PRESSED) || !isPhysicalSignalActive(level, pos)) {
            return 0;
        }
        return 15;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!state.getValue(PRESSED) || getConnectedDirection(state) != direction || !isPhysicalSignalActive(level, pos)) {
            return 0;
        }
        return 15;
    }

    /** Suppressed in link-only mode, so the wire stays silent while Create carries the pulse instead. */
    private static boolean isPhysicalSignalActive(BlockGetter level, BlockPos pos) {
        return !(level.getBlockEntity(pos) instanceof CardReaderBlockEntity reader) || reader.isPhysicalSignalActive();
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
        DKTooltips.summary(tooltip, "card_reader1", "card_reader2", "card_reader3", "card_reader_wrench_pickup");
    }
}
