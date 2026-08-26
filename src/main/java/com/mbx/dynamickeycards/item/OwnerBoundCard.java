package com.mbx.dynamickeycards.item;

import com.mbx.dynamickeycards.DKMessages;
import com.mbx.dynamickeycards.DKSounds;
import com.mbx.dynamickeycards.registry.DKComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The two-click "bind to the activating player" flow shared by {@link EstateKeycardItem} and
 * {@link EstateMaintenanceCardItem} - identical in both (same components, same messages), just
 * applied to a different interaction tier (card registration/passage vs. wrench-tier
 * maintenance) once bound. Pulled out here rather than a common superclass since
 * {@code EstateKeycardItem} already extends {@link KeycardItem} (needed so the card reader's own
 * {@code instanceof KeycardItem} dispatch gate lets it through at all) - Java only allows one
 * parent, so a shared static helper is what actually avoids the duplication.
 */
final class OwnerBoundCard {

    /** Length of the confirmation window, in ticks. */
    private static final int CONFIRM_TICKS = 100;

    private OwnerBoundCard() {
    }

    /** The player this card is bound to, or {@code null} if it hasn't been activated yet. */
    @Nullable
    static UUID boundOwner(ItemStack stack) {
        return stack.get(DKComponents.BOUND_OWNER.get());
    }

    static InteractionResultHolder<ItemStack> activate(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (boundOwner(stack) != null) {
            // already bound — the foil (isFoil) already shows this visually, so this
            // right-click is a silent no-op rather than repeating it as an actionbar message
            return InteractionResultHolder.sidedSuccess(stack, false);
        }
        Long deadline = stack.get(DKComponents.ACTIVATION_DEADLINE.get());
        if (deadline != null && level.getGameTime() <= deadline) {
            // confirmed within the window: bind to this player
            stack.remove(DKComponents.ACTIVATION_DEADLINE.get());
            stack.set(DKComponents.BOUND_OWNER.get(), player.getUUID());
            DKMessages.actionBar(player, "dynamickeycards.estate.registered", ChatFormatting.GREEN, player.getName());
            DKSounds.confirm(level, player.blockPosition());
        } else {
            // first click: ask for confirmation
            stack.set(DKComponents.ACTIVATION_DEADLINE.get(), level.getGameTime() + CONFIRM_TICKS);
            DKMessages.actionBar(player, "dynamickeycards.estate.confirm", ChatFormatting.WHITE);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }
}
