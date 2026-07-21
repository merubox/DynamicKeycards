package com.mbx.dynamickeycards.item;

import com.mbx.dynamickeycards.DKTooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The crew's master card. Blank until first registered, which mints the crew's group
 * key; from then on its registrations apply to the whole crew, because every issued
 * member card carries the group key as an inherited key. In the duplicator it is never
 * re-keyed: blank keycards become crew members, blank manager cards become co-managers
 * (exact clones of the group key). Possession is authority — there is no owner binding.
 */
public class CrewManagerKeycardItem extends KeycardItem {

    public CrewManagerKeycardItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        appendKeyInfo(stack, tooltip);
        DKTooltips.summary(tooltip, "crew_manager1", "crew_manager2");
    }
}
