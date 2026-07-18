package com.mbx.dynamickeycards.item;

import com.mbx.dynamickeycards.DKTooltips;
import com.mbx.dynamickeycards.registry.DKComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.UUID;

/**
 * A keycard in one of the 16 vanilla colors. Blank until first registered on a card
 * reader, which stamps it with a unique {@link DKComponents#CARD_ID}; readers only pass
 * keycards whose id they have registered.
 */
public class KeycardItem extends Item {

    public KeycardItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        UUID cardId = stack.get(DKComponents.CARD_ID.get());
        if (cardId != null) {
            tooltip.add(Component.translatable("dynamickeycards.tooltip.keycard_id",
                    cardId.toString().substring(0, 8)).withStyle(ChatFormatting.DARK_GRAY));
        }
        DKTooltips.summary(tooltip, "keycard");
    }
}
