package com.mbx.dynamickeycards.item;

import com.mbx.dynamickeycards.DKMessages;
import com.mbx.dynamickeycards.DKSounds;
import com.mbx.dynamickeycards.DKTooltips;
import com.mbx.dynamickeycards.registry.DKComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The item form of a receiver, before it's ever placed. Right-clicking an existing card reader,
 * transmitter, or advanced sensor (anything implementing {@code block.SignalSource}) while
 * holding one binds it - handled in each target's own block class, not here, same reasoning as
 * {@code BoundSensorBlockItem}. Placing it then carries the bound id into the new block entity,
 * see each block's {@code setPlacedBy}.
 */
public class ReceiverBlockItem extends BlockItem {

    public ReceiverBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    public void bindTo(ItemStack stack, UUID sourceId) {
        stack.set(DKComponents.BOUND_SOURCE.get(), sourceId);
    }

    @Nullable
    public static UUID boundSource(ItemStack stack) {
        return stack.get(DKComponents.BOUND_SOURCE.get());
    }

    /** Enchant-glint shimmer once bound, so it's identifiable at a glance in an inventory. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return boundSource(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (boundSource(stack) != null) {
            tooltip.add(Component.translatable("dynamickeycards.tooltip.device_linked").withStyle(ChatFormatting.DARK_GRAY));
        }
        DKTooltips.summary(tooltip, "receiver1", "receiver2");
    }

    /** Right-clicking empty air while already bound clears the binding. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (boundSource(stack) == null) {
            return super.use(level, player, hand);
        }
        if (!level.isClientSide) {
            stack.remove(DKComponents.BOUND_SOURCE.get());
            DKMessages.actionBar(player, "dynamickeycards.link_device.cancelled", ChatFormatting.WHITE);
            DKSounds.remove(level, player.blockPosition());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
