package com.mbx.dynamickeycards;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A dark gray "Hold [Shift] for Summary" hint line (key name in gray) that expands to
 * gray summary lines while Shift is held. Only ever called from item tooltip rendering,
 * so the client-only {@link Screen} reference is safe.
 */
public class DKTooltips {

    public static void summary(List<Component> tooltip, String... summaryKeys) {
        if (Screen.hasShiftDown()) {
            for (String key : summaryKeys) {
                tooltip.add(Component.translatable("dynamickeycards.tooltip." + key).withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.translatable("dynamickeycards.tooltip.hold_for_summary",
                            Component.translatable("dynamickeycards.tooltip.key_shift").withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
