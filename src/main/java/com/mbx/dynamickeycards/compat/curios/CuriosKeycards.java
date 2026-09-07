package com.mbx.dynamickeycards.compat.curios;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.List;
import java.util.function.Predicate;

/**
 * Reads what a player is wearing in Curios' {@code necklace} slot - an ID-badge-on-a-lanyard
 * position, which is the whole point of the integration.
 *
 * <p>Only ever reached through {@link CuriosAvailability#isLoaded()}: every Curios type this
 * mod mentions lives in this class, so nothing links it when Curios is absent.
 *
 * <p>Worn cards let their holder <em>pass</em> a reader with an empty hand, and nothing more.
 * Configuring a reader (arming register mode, registering or removing a card, resetting) still
 * needs the card in hand - see {@code CardReaderBlock#useWithoutItem}. That split is what keeps
 * a wearer from silently administering readers just by walking up to them, and it also keeps
 * "which card did I just register?" unambiguous.
 */
public final class CuriosKeycards {

    /** The slot a keycard hangs in. Curios ships this slot type; the data pack tag opts our cards into it. */
    private static final String SLOT = "necklace";

    private CuriosKeycards() {
    }

    /**
     * What the player is wearing in the necklace slot, as far as {@code accepted} is concerned:
     * {@code TRUE} if any worn card passes, {@code FALSE} if cards are worn but none passes, and
     * {@code null} if nothing is worn there at all.
     *
     * <p>Three states rather than a boolean because the caller treats "wearing nothing" and
     * "wearing a card that isn't registered" differently - the first has to stay the do-nothing
     * this interaction always was, the second is refused the same way holding that card would be.
     */
    @Nullable
    public static Boolean acceptedWornCard(Player player, Predicate<ItemStack> accepted) {
        List<ItemStack> worn = CuriosApi.getCuriosInventory(player)
                .map(inventory -> inventory.findCurios(SLOT).stream()
                        .map(SlotResult::stack)
                        .filter(stack -> !stack.isEmpty())
                        .toList())
                .orElse(List.of());
        if (worn.isEmpty()) {
            return null;
        }
        return worn.stream().anyMatch(accepted);
    }
}
