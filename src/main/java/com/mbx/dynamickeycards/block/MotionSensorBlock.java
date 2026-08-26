package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DKNetwork;

import com.mbx.dynamickeycards.DKSounds;
import com.mbx.dynamickeycards.item.BoundSensorBlockItem;
import com.mbx.dynamickeycards.registry.DKBlockEntities;
import com.mbx.dynamickeycards.registry.DKComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Shared shape between {@link WallSensorBlock} and {@link CeilingSensorBlock}: both detect
 * living entities in a fixed column (their own cell plus the one directly below) and drive a
 * redstone signal from it, wall/button-style - strong power only into whatever they're mounted
 * on, weak power in every direction. {@link MotionSensorBlockEntity} does the actual per-tick
 * detection; this interface only holds what both blocks need to agree on to make that generic.
 */
public interface MotionSensorBlock {

    BooleanProperty PRESENT = BooleanProperty.create("present");

    /** Direction pointing away from this sensor's mounting surface, into open space. */
    Direction openDirection(BlockState state);

    default BlockEntity newMotionSensorBlockEntity(BlockPos pos, BlockState state) {
        return new MotionSensorBlockEntity(pos, state);
    }

    default <T extends BlockEntity> BlockEntityTicker<T> motionSensorTicker(Level level, BlockEntityType<T> type) {
        if (level.isClientSide || type != DKBlockEntities.MOTION_SENSOR.get()) {
            return null;
        }
        return (lvl, pos, state, be) -> {
            if (be instanceof MotionSensorBlockEntity sensor) {
                MotionSensorBlockEntity.tick(lvl, pos, state, sensor);
            }
        };
    }

    /** Suppressed in link-only mode, so the wire stays silent while Create carries the signal instead. */
    static boolean isPhysicalSignalActive(BlockGetter level, BlockPos pos) {
        return !(level.getBlockEntity(pos) instanceof MotionSensorBlockEntity sensor) || sensor.getSignalMode().physicalActive;
    }

    /**
     * A held, still-unplaced advanced sensor item ({@link BoundSensorBlockItem}) binds it to
     * this block instead of any of the usual wrench handling - {@code null} if {@code stack}
     * isn't one, the same "not applicable" convention {@code AdvancedSensorDyeing#tryDyeInteraction}
     * uses. Works the same whether this block is a plain or advanced sensor - see
     * {@link AdvancedSensorBlockEntity#applyPlacedBinding} for what happens once the item is
     * actually placed. Reacts regardless of whether Create is loaded, unlike the wrench
     * handling below it - binding sensors together doesn't depend on Create at all.
     *
     * <p>Refuses outright if this block is an advanced sensor that's already bound to something
     * (a reader or another sensor) - an advanced sensor is bound to exactly one thing at a time,
     * on either end of the relationship. Without this check, binding a second sensor to an
     * already-bound one would silently steal its single outgoing slot and sever whatever it was
     * already driving (see {@link AdvancedSensorBlockEntity#applyPlacedBinding}'s own doc) -
     * surprising and destructive for no benefit, since the same reach is available by just
     * binding directly to the reader instead.
     *
     * <p>Also simply doesn't apply at all if this block is a <em>plain</em> sensor (falls through
     * to whatever this item's own default block interaction is, e.g. placing itself against this
     * block like any other item would) - a plain sensor is no longer a valid bind target at all
     * (only advanced-to-advanced binding remains). A receiver bound directly to whichever advanced
     * sensor would have driven it plays that same "remote relay" role now, without needing a
     * second, permanently-linked sensor in between. Removing this changes an already-shipped
     * (0.1.6) capability, so an existing world's plain-sensor targets simply stop accepting new
     * bindings going forward - any relay already set up before this keeps working exactly as it
     * did (nothing here touches an existing binding). No deny message/sound here, unlike the
     * "already bound" case below - a plain sensor was never a target to begin with, so there's
     * nothing to explain; it should read the same as right-clicking any other ordinary block.
     */
    @Nullable
    default ItemInteractionResult tryBindItemInteraction(ItemStack stack, Level level, BlockPos pos, Player player) {
        if (!(stack.getItem() instanceof BoundSensorBlockItem sensorItem)) {
            return null;
        }
        if (!(level.getBlockEntity(pos) instanceof AdvancedSensorBlockEntity target)) {
            return null;
        }
        if (target.getBoundReader() != null || target.getBoundSensor() != null) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("dynamickeycards.link_device.already_bound").withStyle(ChatFormatting.RED), true);
                DKSounds.deny(level, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            sensorItem.bindToSensor(stack, target.getDeviceId());
            // forces the held-item resync immediately - see CardReaderBlock's sensor-bind case
            // for why this matters (otherwise the bind-target highlight can miss the moment of
            // binding, only catching up on the next automatic per-tick sync or a reconnect)
            player.containerMenu.broadcastChanges();
            DKNetwork.registerDevicePosition(player, target.getDeviceId(), pos);
            // white, not green: this only tunes the held item, the actual connection isn't
            // "complete" (green) until it's placed - see AdvancedSensorBlockEntity#applyPlacedBinding.
            // No sound here - only the completed connection plays one.
            player.displayClientMessage(
                    Component.translatable("dynamickeycards.link_device.tuned").withStyle(ChatFormatting.WHITE), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * A held redstone dust right-clicking this sensor arms a range edit - {@code null} if
     * {@code stack} isn't redstone dust or this isn't an advanced sensor ({@link AdvancedSensorDyeing}),
     * same "not applicable" convention as {@link #tryBindItemInteraction}. Plain sensors keep
     * their fixed column forever - the envelope range editing opens up is only worth the extra
     * interaction on the tier that's already asking for more setup (binding, dyeing) anyway.
     * Free (nothing consumed, nothing changes yet - see
     * {@code MotionSensorBlockEntity#enterRangeEdit}) and marks the stack with an enchant glint
     * (see {@link DataComponents#ENCHANTMENT_GLINT_OVERRIDE}) so it reads as "this dust is
     * spoken for" - cleared again on confirm/cancel, see {@code DKNetwork#handleCommit}. Once
     * already armed, this does nothing further - confirming/cancelling now happens by clicking
     * the highlight itself (see {@code SensorRangeClientHandler#onClickInput}), not by
     * re-clicking the block, so a second right-click here is just swallowed (rather than falling
     * through to placing a redstone wire on top of the sensor).
     *
     * <p>A sensor currently bound to a reader can only be edited while that reader is in
     * register mode - the same consent gate linking a new device to a reader already requires
     * (see {@code CardReaderBlock#useItemOn}), checked again here since changing a bound
     * sensor's range changes what the reader effectively reacts to, same as a bind would.
     *
     * <p>If {@code stack} is already tagged for a <em>different</em> sensor that's still armed,
     * that one is cancelled first (a clean handoff) rather than just overwriting the tag and
     * leaving it silently stuck in edit mode forever - nothing else would ever tell it to stop,
     * since the only thing tracking it (the tag) just moved elsewhere. Silent on purpose - no
     * message, no sound - since this is one continuous action from the player's own perspective
     * (moving on to the next sensor), not two: the old sensor's cancel sound and the new one's
     * arm sound firing back to back read as a jarring overlap rather than a single action.
     *
     * <p>If {@code sensor} is already armed (whether by this stack or a lost/orphaned one from
     * before this handoff existed), this just tells the player how to get it unstuck rather than
     * silently doing nothing - there's no redstone in hand that still points at it once the tag's
     * gone, so the normal handoff above can't reach it either.
     */
    @Nullable
    default ItemInteractionResult tryRangeEditInteraction(ItemStack stack, Level level, BlockPos pos, Player player) {
        if (!stack.is(Items.REDSTONE) || !(this instanceof AdvancedSensorDyeing)
                || !(level.getBlockEntity(pos) instanceof MotionSensorBlockEntity sensor)) {
            return null;
        }
        if (sensor.isRangeEditMode()) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("dynamickeycards.sensor.range_already_editing").withStyle(ChatFormatting.RED), true);
                DKSounds.deny(level, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!canEditRange(level, sensor)) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("dynamickeycards.link_device.needs_register_mode").withStyle(ChatFormatting.RED), true);
                DKSounds.deny(level, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            BlockPos previousTarget = stack.get(DKComponents.RANGE_EDIT_TARGET.get());
            if (previousTarget != null && !previousTarget.equals(pos)
                    && level.getBlockEntity(previousTarget) instanceof MotionSensorBlockEntity previousSensor
                    && previousSensor.isRangeEditMode()) {
                previousSensor.cancelRangeEdit();
            }
            sensor.enterRangeEdit();
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            stack.set(DKComponents.RANGE_EDIT_TARGET.get(), pos);
            player.displayClientMessage(
                    Component.translatable("dynamickeycards.sensor.range_edit_prompt").withStyle(ChatFormatting.WHITE), true);
            DKSounds.arm(level, pos);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Any bare-hand click while a range edit is armed cancels it, same convention as
     * {@code CardReaderBlock}'s register mode cancel - a redundant fallback alongside the
     * primary click-the-highlight cancel (see {@code SensorRangeClientHandler#onClickInput}).
     * Clears the armed dust's glint/target marker wherever it actually is in the player's
     * inventory (see {@link #clearRangeEditMarkers}), not just the hand that triggered this -
     * a bare-hand click by definition isn't holding it, and even a held-item cancel could come
     * from whichever hand doesn't have it. {@code false} if there was nothing to cancel, so the
     * caller can fall through to whatever bare-hand behavior it would otherwise have.
     */
    default boolean tryCancelRangeEdit(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof MotionSensorBlockEntity sensor) || !sensor.isRangeEditMode()) {
            return false;
        }
        if (!level.isClientSide) {
            sensor.cancelRangeEdit();
            clearRangeEditMarkers(player, pos);
            player.displayClientMessage(
                    Component.translatable("dynamickeycards.sensor.range_cancelled").withStyle(ChatFormatting.WHITE), true);
            DKSounds.remove(level, pos);
        }
        return true;
    }

    /** Whether {@code sensor} is currently allowed to have its range edited - see {@link #tryRangeEditInteraction}. */
    static boolean canEditRange(Level level, MotionSensorBlockEntity sensor) {
        if (!(sensor instanceof AdvancedSensorBlockEntity advanced) || advanced.getBoundReader() == null) {
            return true;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        BlockPos readerPos = DeviceIndex.get(serverLevel).getPosition(advanced.getBoundReader());
        return readerPos != null && level.getBlockEntity(readerPos) instanceof CardReaderBlockEntity reader && reader.isRegisterMode();
    }

    /**
     * Clears the enchant glint + {@link DKComponents#RANGE_EDIT_TARGET} marker from wherever in
     * {@code player}'s whole inventory a redstone dust stack is currently tagged for {@code pos} -
     * not just whichever hand triggered the cancel/confirm, since that might not be the same hand
     * (or even still a hand at all - a bare-hand cancel, or cancelling/confirming with the tagged
     * dust in the *other* hand) that originally armed it.
     */
    static void clearRangeEditMarkers(Player player, BlockPos pos) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.REDSTONE) && pos.equals(stack.get(DKComponents.RANGE_EDIT_TARGET.get()))) {
                stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
                stack.remove(DKComponents.RANGE_EDIT_TARGET.get());
            }
        }
    }
}
