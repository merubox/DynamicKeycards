package com.mbx.dynamickeycards.item;

import com.mbx.dynamickeycards.DKSounds;
import com.mbx.dynamickeycards.DKTooltips;
import com.mbx.dynamickeycards.registry.DKComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The item form of an advanced sensor (wall or ceiling), before it's ever placed. Binding
 * happens while still held: right-clicking an existing card reader (handled in
 * {@code CardReaderBlock#useItemOn}, not here - see that method for why) stamps this stack with
 * the reader's position. Placing it then carries that position into the new block entity, see
 * each block's {@code setPlacedBy}.
 */
public class BoundSensorBlockItem extends SensorBlockItem {

    public BoundSensorBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    public void bindTo(ItemStack stack, BlockPos readerPos) {
        stack.set(DKComponents.BOUND_READER.get(), readerPos);
    }

    @Nullable
    public static BlockPos boundReader(ItemStack stack) {
        return stack.get(DKComponents.BOUND_READER.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (boundReader(stack) != null) {
            tooltip.add(Component.translatable("dynamickeycards.tooltip.advanced_sensor_bound").withStyle(ChatFormatting.DARK_GRAY));
        }
        DKTooltips.summary(tooltip, "sensor1", "sensor2", "sensor_wrench_pickup", "advanced_sensor1", "advanced_sensor2");
    }

    /** Enchant-glint shimmer once bound, so a bound sensor is identifiable at a glance in an inventory. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return boundReader(stack) != null;
    }

    /**
     * Right-clicking empty air while already bound clears the binding. White, not gray, matching
     * the reader's own register-mode-cancelled message ({@code CardReaderBlock#cancelRegisterMode})
     * - this mod's convention for "action undone" feedback.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (boundReader(stack) == null) {
            return super.use(level, player, hand);
        }
        if (!level.isClientSide) {
            stack.remove(DKComponents.BOUND_READER.get());
            player.displayClientMessage(
                    Component.translatable("dynamickeycards.link_device.cancelled").withStyle(ChatFormatting.WHITE), true);
            DKSounds.remove(level, player.blockPosition());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
