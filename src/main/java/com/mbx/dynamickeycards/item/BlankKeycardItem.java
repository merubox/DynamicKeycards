package com.mbx.dynamickeycards.item;

import com.mbx.dynamickeycards.DKTooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The craftable base card. Carries no key; registering it on a card reader turns it into
 * the same-color {@link KeycardItem}, and duplicating a card onto it turns it into a
 * keycard (a fork copy) or a crew member. Dyeing recolors it. Never has a key of its own.
 */
public class BlankKeycardItem extends KeycardItem {

    public BlankKeycardItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        DKTooltips.summary(tooltip, "blank_keycard");
    }
}
