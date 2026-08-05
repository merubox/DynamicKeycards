package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DKSounds;
import com.mbx.dynamickeycards.menu.LinkDeviceMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared Create-wrench behavior for every {@link LinkDeviceBlockEntity} block (the card reader,
 * and both motion sensors): standing opens {@link LinkDeviceMenu}, sneaking picks the block up
 * into the player's inventory after a confirming second click. Callers that need an extra guard
 * before allowing the pickup (the reader checks ownership, to protect its access-control config)
 * layer it on top of {@link #wrenchPickup} rather than this interface knowing about it.
 */
public interface WrenchConfigurableBlock {

    /**
     * Standing + wrench: opens the config menu, cancelling any pending wrench-pickup
     * confirmation first - opening the UI counts as "doing something else" with this device.
     */
    default ItemInteractionResult openLinkDeviceMenu(BlockState state, Level level, BlockPos pos,
                                                       Player player, LinkDeviceBlockEntity device) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            device.clearPendingActions();
            MenuProvider provider = new SimpleMenuProvider(
                    (containerId, playerInventory, opener) -> new LinkDeviceMenu(containerId, playerInventory, device),
                    state.getBlock().getName());
            serverPlayer.openMenu(provider, buf -> buf.writeBlockPos(pos));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Sneak + wrench: a confirming second click moves the block into the player's inventory
     * instead of breaking it normally; the first click just warns.
     */
    default ItemInteractionResult wrenchPickup(BlockState state, Level level, BlockPos pos,
                                                Player player, LinkDeviceBlockEntity device) {
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
