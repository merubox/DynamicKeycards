package com.mbx.dynamickeycards.item;

import com.mbx.dynamickeycards.DKMessages;
import com.mbx.dynamickeycards.DKSounds;
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
 * The item form of a card reader, before it's ever placed. Right-clicking an existing reader
 * while holding one stamps this stack with that reader's position (handled in
 * {@code CardReaderBlock#useItemOn}, not here - same reasoning as {@code BoundSensorBlockItem}).
 * Placing it then links the two readers to each other, see {@code CardReaderBlock#setPlacedBy}.
 *
 * <p>Linking only shares which cards are registered - each reader keeps its own owner,
 * mode/frequency, and pulse, and reacts independently to its own taps. It isn't a way to make
 * two readers fire together.
 */
public class LinkedReaderBlockItem extends BlockItem {

    public LinkedReaderBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    public void linkTo(ItemStack stack, UUID readerId) {
        stack.set(DKComponents.LINKED_READER.get(), readerId);
    }

    @Nullable
    public static UUID linkedReader(ItemStack stack) {
        return stack.get(DKComponents.LINKED_READER.get());
    }

    /** Enchant-glint shimmer once set to link, so it's identifiable at a glance in an inventory. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return linkedReader(stack) != null;
    }

    /**
     * Same "Device linked" line {@code BoundSensorBlockItem} shows once tuned - this item only
     * had the foil shimmer for that before, with no text explaining it. The rest of the tooltip
     * ({@code card_reader1/2/3}, wrench pickup) comes from {@code CardReaderBlock}'s own
     * {@code appendHoverText}, which {@link BlockItem}'s default already delegates to - so this
     * only needs to add the one extra line, not duplicate the block's summary keys.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (linkedReader(stack) != null) {
            tooltip.add(Component.translatable("dynamickeycards.tooltip.device_linked").withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    /** Right-clicking empty air while already set to link clears it. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (linkedReader(stack) == null) {
            return super.use(level, player, hand);
        }
        if (!level.isClientSide) {
            stack.remove(DKComponents.LINKED_READER.get());
            DKMessages.actionBar(player, "dynamickeycards.link_device.cancelled", ChatFormatting.WHITE);
            DKSounds.remove(level, player.blockPosition());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
