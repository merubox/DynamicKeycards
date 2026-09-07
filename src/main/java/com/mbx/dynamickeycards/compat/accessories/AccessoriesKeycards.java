package com.mbx.dynamickeycards.compat.accessories;

import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.slot.SlotEntryReference;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * The Accessories half of {@code WornKeycards}, reading its {@code necklace} slot. Every
 * Accessories type this mod mentions lives here, reached only through
 * {@link AccessoriesAvailability#isLoaded()}.
 */
public final class AccessoriesKeycards {

    private static final String SLOT = "necklace";

    private AccessoriesKeycards() {
    }

    /** Same three-state answer as the Curios side - see {@code WornKeycards#acceptedWornCard}. */
    @Nullable
    public static Boolean acceptedWornCard(Player player, Predicate<ItemStack> accepted) {
        List<ItemStack> worn = AccessoriesCapability.getOptionally(player)
                .map(capability -> capability.getAllEquipped().stream()
                        .filter(entry -> SLOT.equals(entry.reference().slotName()))
                        .map(SlotEntryReference::stack)
                        .filter(stack -> !stack.isEmpty())
                        .toList())
                .orElse(List.of());
        if (worn.isEmpty()) {
            return null;
        }
        return worn.stream().anyMatch(accepted);
    }
}
