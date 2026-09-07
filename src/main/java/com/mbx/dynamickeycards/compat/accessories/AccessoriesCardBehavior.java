package com.mbx.dynamickeycards.compat.accessories;

import com.mbx.dynamickeycards.registry.DKItems;
import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.AccessoriesAPI;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Turns off Accessories' right-click-to-equip for the cards - the counterpart to what
 * {@code CuriosSlotTooltip} does on the Curios side.
 *
 * <p>A card in hand already means a dozen things depending on what it is pointed at and whether
 * the player is sneaking; silently moving it onto the body on a misclick would be one interaction
 * too many, and an unnoticed one - the card leaves the hand without a message. Equipping through
 * the accessory screen stays.
 *
 * <p>Accessories has no slot-tooltip opt-out to pair with this; that line is removed after the
 * fact instead, in {@code DKTooltipCleanup}.
 */
public final class AccessoriesCardBehavior {

    /** Default in every respect except that right-click never equips it. */
    private static final Accessory NOT_FROM_USE = new Accessory() {
        @Override
        public boolean canEquipFromUse(ItemStack stack) {
            return false;
        }
    };

    private AccessoriesCardBehavior() {
    }

    public static void register() {
        for (Item card : DKItems.wearableCards()) {
            AccessoriesAPI.registerAccessory(card, NOT_FROM_USE);
        }
    }
}
