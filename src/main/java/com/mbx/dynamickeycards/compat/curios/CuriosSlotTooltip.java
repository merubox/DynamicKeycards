package com.mbx.dynamickeycards.compat.curios;

import com.mbx.dynamickeycards.registry.DKItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;

import java.util.List;

/**
 * Drops Curios' own "Slot: Necklace" tooltip line from the cards.
 *
 * <p>Every card already says what it is and what it opens; the slot line adds a fourth line
 * repeating something the accessory screen shows anyway, and it appears on all 66 cards.
 *
 * <p>Attaching an {@link ICurio} is the only way to opt out - Curios reads that line from the
 * item's own capability, so a card has to provide one just to return nothing.
 *
 * <p>The same capability also turns off right-click-to-equip. A card in hand already means a
 * dozen things depending on what it is pointed at and whether the player is sneaking; silently
 * moving it onto the body on a misclick would be one interaction too many, and an unnoticed one -
 * the card leaves the hand without a message. Equipping through the accessory screen stays.
 */
public final class CuriosSlotTooltip {

    private CuriosSlotTooltip() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        for (Item card : DKItems.wearableCards()) {
            event.registerItem(CuriosCapability.ITEM, (stack, context) -> new SilentCurio(stack), card);
        }
    }

    /** Default in every respect except the slot line and right-click equipping. */
    private record SilentCurio(ItemStack stack) implements ICurio {

        @Override
        public ItemStack getStack() {
            return stack;
        }

        @Override
        public List<Component> getSlotsTooltip(List<Component> tooltips, Item.TooltipContext context) {
            return List.of();
        }

        @Override
        public boolean canEquipFromUse(SlotContext context) {
            return false;
        }
    }
}
