package com.mbx.dynamickeycards.event;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.registry.DKItems;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;
import java.util.Set;

/**
 * Drops the Accessories mod's "Slot: Necklace" line from this mod's own cards.
 *
 * <p>Curios offers a proper opt-out ({@code CuriosSlotTooltip}); Accessories has none - it appends
 * the line from a {@link ItemTooltipEvent} handler of its own, so the only way past it is to run
 * afterwards and take the line back out. Hence {@link EventPriority#LOWEST}.
 *
 * <p><b>Only ever touches this mod's items.</b> The stack is checked against
 * {@link DKItems#wearableCards()} before anything is removed, so another mod's tooltips are never
 * examined, let alone edited.
 *
 * <p>Matching is by translation key rather than rendered text, so it holds in every language. No
 * Accessories type is named here at all - the key is just a string - which is why this can live
 * outside {@code compat/} and needs no availability guard: with Accessories absent, no line
 * matches and this does nothing.
 */
@EventBusSubscriber(modid = DynamicKeycards.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class DKTooltipCleanup {

    /** What Accessories builds its slot line from - see its {@code accessories.slot.tooltip.*} keys. */
    private static final String ACCESSORIES_SLOT_PREFIX = "accessories.slot.tooltip";
    private static final String ACCESSORIES_COSMETIC_PREFIX = "accessories.cosmetic_slot.tooltip";

    private static Set<net.minecraft.world.item.Item> wearable;

    private DKTooltipCleanup() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onItemTooltip(ItemTooltipEvent event) {
        if (wearable == null) {
            wearable = Set.copyOf(DKItems.wearableCards());
        }
        if (!wearable.contains(event.getItemStack().getItem())) {
            return;
        }
        event.getToolTip().removeIf(DKTooltipCleanup::isSlotLine);
    }

    /** Whether this line, or anything nested in it, is one of the Accessories slot labels. */
    private static boolean isSlotLine(Component line) {
        if (line.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            if (key.startsWith(ACCESSORIES_SLOT_PREFIX) || key.startsWith(ACCESSORIES_COSMETIC_PREFIX)) {
                return true;
            }
        }
        List<Component> siblings = line.getSiblings();
        for (Component sibling : siblings) {
            if (isSlotLine(sibling)) {
                return true;
            }
        }
        return false;
    }
}
