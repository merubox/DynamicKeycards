package com.mbx.dynamickeycards.item;

import com.mbx.dynamickeycards.DKTooltips;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * An owner-scoped master key for the *maintenance* tier - opening a card reader's (or a reader-
 * bound peripheral's) wrench config UI, or picking it up - the exact counterpart
 * {@link EstateKeycardItem} is for the key tier (register mode, card registration, reset).
 * Binding works identically (same two-click confirm, see {@link OwnerBoundCard}); the two cards
 * are deliberately separate items rather than one card doing both, so an owner can hand out
 * maintenance access (reconfigure/remove) without also handing out key access (who's allowed
 * through), or vice versa. Not a {@link KeycardItem} - it never taps a reader for passage, so it
 * doesn't need that class's card-key machinery, and skips the reader's {@code instanceof
 * KeycardItem} dispatch gate entirely (it's checked earlier, alongside the wrench).
 */
public class EstateMaintenanceCardItem extends Item {

    public EstateMaintenanceCardItem(Properties properties) {
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
        DKTooltips.summary(tooltip, "estate_maintenance1", "estate_maintenance2");
    }
}
