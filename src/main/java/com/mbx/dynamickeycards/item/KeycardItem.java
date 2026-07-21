package com.mbx.dynamickeycards.item;

import com.mbx.dynamickeycards.DKTooltips;
import com.mbx.dynamickeycards.registry.DKComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A keycard in one of the 16 vanilla colors. Blank until first registered on a card
 * reader, which stamps it with its own unique {@link DKComponents#CARD_ID}. Duplication
 * forks a card: both sides keep the keys shared so far ({@link DKComponents#INHERITED_KEYS})
 * but each carries a fresh own key afterwards, so registrations never propagate between
 * them. Readers match any carried key; per-card blocking targets the own key only.
 */
public class KeycardItem extends Item {

    public KeycardItem(Properties properties) {
        super(properties);
    }

    /** The card's own key: the one stamped by registrations, and the one blocking targets. */
    @Nullable
    public static UUID ownKey(ItemStack stack) {
        return stack.get(DKComponents.CARD_ID.get());
    }

    /** Keys inherited through duplication (empty for never-copied cards). */
    public static List<UUID> inheritedKeys(ItemStack stack) {
        return stack.getOrDefault(DKComponents.INHERITED_KEYS.get(), List.of());
    }

    /** Own key + inherited keys; empty for a blank card. */
    public static List<UUID> allKeys(ItemStack stack) {
        UUID own = ownKey(stack);
        if (own == null) {
            return List.of();
        }
        List<UUID> keys = new ArrayList<>(inheritedKeys(stack));
        keys.add(own);
        return keys;
    }

    /** Fork this card: its current own key becomes inherited and a fresh own key is minted. */
    public static void rekey(ItemStack stack) {
        UUID own = ownKey(stack);
        if (own != null) {
            List<UUID> inherited = new ArrayList<>(inheritedKeys(stack));
            inherited.add(own);
            stack.set(DKComponents.INHERITED_KEYS.get(), List.copyOf(inherited));
        }
        stack.set(DKComponents.CARD_ID.get(), UUID.randomUUID());
    }

    /** Stamp a duplicated card: it inherits the given keys and mints a fresh own key. */
    public static void inheritFrom(ItemStack stack, List<UUID> keys) {
        stack.set(DKComponents.INHERITED_KEYS.get(), List.copyOf(keys));
        stack.set(DKComponents.CARD_ID.get(), UUID.randomUUID());
    }

    protected static void appendKeyInfo(ItemStack stack, List<Component> tooltip) {
        UUID cardId = ownKey(stack);
        if (cardId != null) {
            tooltip.add(Component.translatable("dynamickeycards.tooltip.keycard_id",
                    cardId.toString().substring(0, 8)).withStyle(ChatFormatting.DARK_GRAY));
            int inherited = inheritedKeys(stack).size();
            if (inherited > 0) {
                tooltip.add(Component.translatable("dynamickeycards.tooltip.keycard_inherited",
                        inherited).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        appendKeyInfo(stack, tooltip);
        DKTooltips.summary(tooltip, "keycard");
    }
}
