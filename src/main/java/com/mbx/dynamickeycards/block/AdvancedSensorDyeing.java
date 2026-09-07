package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DKSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

/**
 * Shared dye/gold-nugget handling for {@code AdvancedWallSensorBlock} and
 * {@code AdvancedCeilingSensorBlock} - a dye sets {@link AdvancedSensorBlockEntity#getAccentColor}
 * (purely cosmetic), a gold nugget clears it back to the sensor's native undyed look. Both consume
 * one item on use, unless the player is in creative.
 */
interface AdvancedSensorDyeing {

    /**
     * Mirrors {@link AdvancedSensorBlockEntity#getAccentColor} into the blockstate, so the
     * blockstate file can pick the model - see {@link SensorAccent} for why that matters.
     */
    EnumProperty<SensorAccent> ACCENT = EnumProperty.create("accent", SensorAccent.class);

    /** Writes the accent to both places at once - they must never disagree. */
    static void applyAccent(Level level, BlockPos pos, AdvancedSensorBlockEntity sensor, @Nullable DyeColor color) {
        sensor.setAccentColor(color);
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(ACCENT)) {
            level.setBlock(pos, state.setValue(ACCENT, SensorAccent.of(color)), Block.UPDATE_ALL);
        }
    }

    /** {@code null} means "not a dye/nugget interaction" - fall through to whatever handles the item next. */
    @Nullable
    default ItemInteractionResult tryDyeInteraction(ItemStack stack, Level level, BlockPos pos,
                                                      Player player, AdvancedSensorBlockEntity sensor) {
        if (stack.getItem() instanceof DyeItem dye) {
            if (!level.isClientSide) {
                applyAccent(level, pos, sensor, dye.getDyeColor());
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(Tags.Items.NUGGETS_GOLD) && sensor.getAccentColor() != null) {
            if (!level.isClientSide) {
                applyAccent(level, pos, sensor, null);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                DKSounds.remove(level, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return null;
    }
}
