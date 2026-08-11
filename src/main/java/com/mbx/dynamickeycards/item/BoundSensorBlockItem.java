package com.mbx.dynamickeycards.item;

import com.mbx.dynamickeycards.DKMessages;
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
 * happens while still held, right-clicking an existing target (handled in the target's own
 * block class, not here - see {@code CardReaderBlock#useItemOn} and
 * {@code MotionSensorBlock#tryBindItemInteraction} for why): either
 * <ul>
 *   <li>a card reader, stamping {@link DKComponents#BOUND_READER} - the sensor drives that
 *   reader's accept pulse remotely; or</li>
 *   <li>another sensor (plain or advanced), stamping {@link DKComponents#BOUND_SENSOR} - the
 *   sensor drives that target's own signal remotely the same way. If the target is itself an
 *   advanced sensor, the binding becomes mutual once placed (see
 *   {@code AdvancedSensorBlockEntity#applyPlacedBinding}) so each drives the other; a plain
 *   target only ever gets driven, never drives back - that asymmetry is why only advanced
 *   sensors carry this item's binding capability in the first place.</li>
 * </ul>
 * The two are mutually exclusive: binding to one clears the other, since a sensor can only be
 * bound to one thing at a time. Placing the item then carries whichever is set into the new
 * block entity, see each block's {@code setPlacedBy}.
 */
public class BoundSensorBlockItem extends SensorBlockItem {

    public BoundSensorBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    public void bindTo(ItemStack stack, BlockPos readerPos) {
        stack.remove(DKComponents.BOUND_SENSOR.get());
        stack.set(DKComponents.BOUND_READER.get(), readerPos);
    }

    @Nullable
    public static BlockPos boundReader(ItemStack stack) {
        return stack.get(DKComponents.BOUND_READER.get());
    }

    public void bindToSensor(ItemStack stack, BlockPos sensorPos) {
        stack.remove(DKComponents.BOUND_READER.get());
        stack.set(DKComponents.BOUND_SENSOR.get(), sensorPos);
    }

    @Nullable
    public static BlockPos boundSensor(ItemStack stack) {
        return stack.get(DKComponents.BOUND_SENSOR.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (boundReader(stack) != null || boundSensor(stack) != null) {
            tooltip.add(Component.translatable("dynamickeycards.tooltip.device_linked").withStyle(ChatFormatting.DARK_GRAY));
        }
        DKTooltips.summary(tooltip, "sensor1", "sensor2", "sensor_wrench_pickup", "advanced_sensor1", "advanced_sensor2");
    }

    /** Enchant-glint shimmer once bound to either kind of target, so it's identifiable at a glance in an inventory. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return boundReader(stack) != null || boundSensor(stack) != null;
    }

    /**
     * Right-clicking empty air while already bound (to either kind of target) clears the
     * binding. White, not gray, matching the reader's own register-mode-cancelled message
     * ({@code CardReaderBlock#cancelRegisterMode}) - this mod's convention for "action undone"
     * feedback.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (boundReader(stack) == null && boundSensor(stack) == null) {
            return super.use(level, player, hand);
        }
        if (!level.isClientSide) {
            stack.remove(DKComponents.BOUND_READER.get());
            stack.remove(DKComponents.BOUND_SENSOR.get());
            DKMessages.actionBar(player, "dynamickeycards.link_device.cancelled", ChatFormatting.WHITE);
            DKSounds.remove(level, player.blockPosition());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
