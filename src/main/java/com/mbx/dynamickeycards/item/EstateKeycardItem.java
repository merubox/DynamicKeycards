package com.mbx.dynamickeycards.item;

import com.mbx.dynamickeycards.DKTooltips;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * An owner-scoped master key. Activating it (right-click) binds it to the activating player;
 * from then on it works exactly like a golden keycard, but only on card readers owned by that
 * player. The binding lives on the card, not the holder, so it keeps working after being
 * handed to someone else. Activation is a two-step confirm to avoid an accidental bind - see
 * {@link OwnerBoundCard} for that shared mechanism.
 */
public class EstateKeycardItem extends KeycardItem {

    public EstateKeycardItem(Properties properties) {
        super(properties);
    }

    /** The player this card is bound to, or {@code null} if it hasn't been activated yet. */
    @Nullable
    public static UUID boundOwner(ItemStack stack) {
        return OwnerBoundCard.boundOwner(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return boundOwner(stack) != null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return OwnerBoundCard.activate(level, player, player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        UUID owner = boundOwner(stack);
        if (owner != null) {
            tooltip.add(Component.translatable("dynamickeycards.tooltip.estate_owner",
                    owner.toString().substring(0, 8)).withStyle(ChatFormatting.DARK_GRAY));
        }
        DKTooltips.summary(tooltip, "estate1", "estate2");
    }
}
