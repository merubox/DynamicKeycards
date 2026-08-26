package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DKSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sneak + wrench (or a maintenance card) picks a device up into the player's inventory instead of
 * breaking it normally - shared by every block that supports it.
 *
 * <p>Callers that need an extra guard before allowing the pickup (the reader and its linked
 * peripherals check {@link MaintenanceAccess}, to protect their access-control config) layer it on
 * top of {@link #wrenchPickup} rather than this interface knowing about it.
 */
public interface WrenchPickupBlock {

    /**
     * The first click only warns and arms the confirmation; a second click while it is still armed
     * completes the pickup. Reuses the block's own break sound and particles (level event 2001) so
     * it reads as a normal removal rather than a silent disappearance.
     */
    default ItemInteractionResult wrenchPickup(BlockState state, Level level, BlockPos pos,
                                               Player player, WrenchPickupTarget device) {
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (!device.isWrenchPickupPending()) {
            device.armWrenchPickupPending();
            player.displayClientMessage(
                    Component.translatable("dynamickeycards.link_device.wrench_pickup_confirm").withStyle(ChatFormatting.RED), true);
            DKSounds.deny(level, pos);
            return ItemInteractionResult.sidedSuccess(false);
        }
        ItemStack pickedUp = new ItemStack(state.getBlock());
        if (!player.getInventory().add(pickedUp)) {
            player.drop(pickedUp, false);
        }
        level.levelEvent(null, 2001, pos, Block.getId(state));
        level.removeBlock(pos, false);
        return ItemInteractionResult.sidedSuccess(false);
    }
}
