package com.mbx.dynamickeycards.event;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.block.CardReaderBlock;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = DynamicKeycards.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class DKEvents {

    /**
     * Card reader clicks must always reach the block, even while sneaking with an item
     * (registration) — and must not be stolen by other mods sharing the sneak+right-click
     * gesture (e.g. Carry On's block pickup). Runs at HIGHEST priority: forces block use
     * over item use, and for the contested sneak+empty-hand click performs the reader
     * interaction immediately and cancels the event so later handlers never see it.
     * (Readers are additionally shipped in the {@code carryon:block_blacklist} tag.)
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof CardReaderBlock)) {
            return;
        }
        event.setUseBlock(TriState.TRUE);
        event.setUseItem(TriState.FALSE);
        if (event.getEntity().isShiftKeyDown()
                && event.getEntity().getMainHandItem().isEmpty()
                && event.getEntity().getOffhandItem().isEmpty()) {
            InteractionResult result = state.useWithoutItem(level, event.getEntity(), event.getHitVec());
            if (result.consumesAction()) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        }
    }
}
