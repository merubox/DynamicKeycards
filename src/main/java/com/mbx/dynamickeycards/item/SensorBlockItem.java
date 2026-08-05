package com.mbx.dynamickeycards.item;

import com.mbx.dynamickeycards.DKTooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Adds the summary tooltip shared by both plain sensor blocks (wall/ceiling) - previously
 * neither had any tooltip at all, unlike every other item in the mod. {@link BoundSensorBlockItem}
 * extends this rather than {@link BlockItem} directly so the advanced sensor's tooltip covers the
 * base detection behavior too, not just the binding-specific part.
 */
public class SensorBlockItem extends BlockItem {

    public SensorBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        DKTooltips.summary(tooltip, "sensor1", "sensor2", "sensor_wrench_pickup");
    }
}
