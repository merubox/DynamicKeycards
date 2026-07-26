package com.mbx.dynamickeycards.item;

import com.mbx.dynamickeycards.DKSounds;
import com.mbx.dynamickeycards.DKTooltips;
import com.mbx.dynamickeycards.registry.DKComponents;
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
 * handed to someone else. Activation is a two-step confirm to avoid an accidental bind.
 */
public class EstateKeycardItem extends KeycardItem {

    /** Length of the confirmation window, in ticks. */
    private static final int CONFIRM_TICKS = 100;

    public EstateKeycardItem(Properties properties) {
        super(properties);
    }

    /** The player this card is bound to, or {@code null} if it hasn't been activated yet. */
    @Nullable
    public static UUID boundOwner(ItemStack stack) {
        return stack.get(DKComponents.BOUND_OWNER.get());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return boundOwner(stack) != null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
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
            message(player, "registered", ChatFormatting.GREEN, player.getName());
            DKSounds.confirm(level, player.blockPosition());
        } else {
            // first click: ask for confirmation
            stack.set(DKComponents.ACTIVATION_DEADLINE.get(), level.getGameTime() + CONFIRM_TICKS);
            message(player, "confirm", ChatFormatting.WHITE);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    private static void message(Player player, String key, ChatFormatting color, Object... args) {
        player.displayClientMessage(Component.translatable("dynamickeycards.estate." + key, args).withStyle(color), true);
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
