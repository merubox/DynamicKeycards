package com.mbx.dynamickeycards.item;

import com.mbx.dynamickeycards.DKTooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Master keycard: passes any card reader regardless of registration or ownership, and
 * sneak-clicking a reader toggles its register mode just like the owner's bare hand.
 * Never carries a card id of its own.
 */
public class GoldenKeycardItem extends KeycardItem {

    public GoldenKeycardItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        DKTooltips.summary(tooltip, "golden_keycard1", "golden_keycard2");
    }
}
