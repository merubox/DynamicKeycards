package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.menu.LinkDeviceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Standing + wrench opens {@link LinkDeviceMenu} - the half of the wrench behavior that only
 * {@link LinkDeviceBlockEntity} blocks have (the card reader and both motion sensors). The
 * sneaking half is {@link WrenchPickupBlock#wrenchPickup}, shared with every other pickable
 * device, and inherited here.
 */
public interface WrenchConfigurableBlock extends WrenchPickupBlock {

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

}
