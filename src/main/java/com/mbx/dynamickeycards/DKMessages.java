package com.mbx.dynamickeycards;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Action-bar feedback text for the card machines, the counterpart to {@link DKSounds}' tones -
 * previously reimplemented separately in each caller (with drifting support for translation
 * args between them) instead of sharing one method.
 */
public final class DKMessages {

    private DKMessages() {
    }

    /** Shows {@code key} (a full translation key, already namespaced) in the action bar, styled with {@code color}. */
    public static void actionBar(Player player, String key, ChatFormatting color, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(color), true);
    }
}
