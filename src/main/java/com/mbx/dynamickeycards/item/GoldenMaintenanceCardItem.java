package com.mbx.dynamickeycards.item;

import com.mbx.dynamickeycards.DKTooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Master maintenance card: opens the wrench config UI, or picks up, any card reader (or a
 * reader-bound peripheral) regardless of ownership - the exact counterpart
 * {@link GoldenKeycardItem} is for the key tier. Never carries an owner binding of its own,
 * unlike {@link EstateMaintenanceCardItem}. Not a {@link KeycardItem} - see
 * {@link EstateMaintenanceCardItem}'s own doc for why.
 */
public class GoldenMaintenanceCardItem extends Item {

    public GoldenMaintenanceCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        DKTooltips.summary(tooltip, "golden_maintenance1", "golden_maintenance2");
    }
}
