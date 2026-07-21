package com.mbx.dynamickeycards.item;

import com.mbx.dynamickeycards.DKTooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A pass-only crew card issued by duplicating a crew manager onto a blank keycard.
 * It follows the manager: wherever the group key is registered, members pass. It can
 * never be registered or duplicated; readers can still shut out a single member (the
 * usual register-mode toggle), and dyeing recycles it into a blank keycard.
 */
public class CrewMemberKeycardItem extends KeycardItem {

    public CrewMemberKeycardItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        appendKeyInfo(stack, tooltip);
        DKTooltips.summary(tooltip, "crew_member1", "crew_member2");
    }
}
