package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.compat.accessories.AccessoriesAvailability;
import com.mbx.dynamickeycards.compat.accessories.AccessoriesKeycards;
import com.mbx.dynamickeycards.compat.curios.CuriosAvailability;
import com.mbx.dynamickeycards.compat.curios.CuriosKeycards;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * "Is the player wearing a keycard?", without the caller knowing which accessory mod answered.
 *
 * <p>Curios and Accessories are separate ecosystems - separate slot tags, separate APIs - so an
 * item opted into one is invisible to the other. Both are supported by declaring the cards in
 * each mod's own {@code necklace} tag and asking each one here; whichever is installed answers,
 * and a pack with both is fine since a card can only be worn in whichever slot actually exists.
 *
 * <p>Nothing outside {@code compat/} is mentioned here beyond the two availability checks - each
 * mod's types stay inside its own compat class so neither is linked when absent.
 */
public final class WornKeycards {

    private WornKeycards() {
    }

    /**
     * {@code TRUE} if a worn card passes {@code accepted}, {@code FALSE} if cards are worn but
     * none passes, {@code null} if nothing is worn (or no accessory mod is installed).
     *
     * <p>Three states rather than a boolean because "wearing nothing" has to stay the do-nothing
     * that an empty-handed click has always been, while "wearing a card that isn't registered" is
     * refused the same way holding that card would be.
     */
    @Nullable
    public static Boolean acceptedWornCard(Player player, Predicate<ItemStack> accepted) {
        Boolean curios = CuriosAvailability.isLoaded()
                ? CuriosKeycards.acceptedWornCard(player, accepted) : null;
        Boolean accessories = AccessoriesAvailability.isLoaded()
                ? AccessoriesKeycards.acceptedWornCard(player, accepted) : null;
        return combine(curios, accessories);
    }

    /**
     * Both mods have to be asked before answering, and a refusal from one must not shadow a pass
     * from the other: with both installed a player has two necklace slots, and an unregistered
     * card in one used to make the reader refuse a registered card in the other.
     *
     * <p>So a pass anywhere wins, {@code null} only when neither has anything worn, and
     * {@code FALSE} when cards are worn but none of them opens this reader.
     */
    @Nullable
    private static Boolean combine(@Nullable Boolean first, @Nullable Boolean second) {
        if (Boolean.TRUE.equals(first) || Boolean.TRUE.equals(second)) {
            return Boolean.TRUE;
        }
        if (first == null && second == null) {
            return null;
        }
        return Boolean.FALSE;
    }
}
