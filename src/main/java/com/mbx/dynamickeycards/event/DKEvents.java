package com.mbx.dynamickeycards.event;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.block.CardDuplicatorBlock;
import com.mbx.dynamickeycards.block.CardReaderBlock;
import com.mbx.dynamickeycards.block.CeilingSensorBlock;
import com.mbx.dynamickeycards.block.ReceiverBlock;
import com.mbx.dynamickeycards.block.TransmitterBlock;
import com.mbx.dynamickeycards.block.WallSensorBlock;
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
     * Card reader clicks (registration, sneak+empty-hand) and every other device's own wrench
     * clicks (mode UI / pickup) must always reach the block — never stolen by other mods sharing
     * the same gestures (e.g. Carry On's own sneak+right-click and wrench-tool block pickup).
     * Runs at HIGHEST priority: forces block use over item use for all of them (so a wrench in
     * hand still reaches {@code useItemOn} instead of Carry On intercepting it - this is what was
     * actually missing for the two sensor blocks, letting Carry On silently swallow their own
     * wrench-pickup clicks before {@code useItemOn} ever ran), and additionally short-circuits
     * the contested sneak+empty-hand click (the card reader/duplicator's own gesture) by
     * performing the block's interaction immediately and cancelling the event so later handlers
     * never see it. (These blocks are additionally shipped in the {@code carryon:block_blacklist}
     * tag - both protections are needed since a blacklist tag alone doesn't stop *other* mods
     * that don't respect it, and forcing use-block alone doesn't stop Carry On's own listener if
     * it runs at an even higher priority than this one.)
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof CardReaderBlock || state.getBlock() instanceof CardDuplicatorBlock
                || state.getBlock() instanceof TransmitterBlock || state.getBlock() instanceof ReceiverBlock
                || state.getBlock() instanceof WallSensorBlock || state.getBlock() instanceof CeilingSensorBlock)) {
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
