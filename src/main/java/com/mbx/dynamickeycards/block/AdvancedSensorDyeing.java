package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DKSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

/**
 * Shared dye/gold-nugget handling for {@code AdvancedWallSensorBlock} and
 * {@code AdvancedCeilingSensorBlock} - a dye sets {@link AdvancedSensorBlockEntity#getAccentColor}
 * (purely cosmetic, see {@code AdvancedSensorRenderer}), a gold nugget clears it back to the
 * sensor's native undyed look. Both consume one item on use, unless the player is in creative.
 */
interface AdvancedSensorDyeing {

    /** {@code null} means "not a dye/nugget interaction" - fall through to whatever handles the item next. */
    @Nullable
    default ItemInteractionResult tryDyeInteraction(ItemStack stack, Level level, BlockPos pos,
                                                      Player player, AdvancedSensorBlockEntity sensor) {
        if (stack.getItem() instanceof DyeItem dye) {
            if (!level.isClientSide) {
                sensor.setAccentColor(dye.getDyeColor());
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                DKSounds.confirm(level, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(Tags.Items.NUGGETS_GOLD) && sensor.getAccentColor() != null) {
            if (!level.isClientSide) {
                sensor.setAccentColor(null);
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
