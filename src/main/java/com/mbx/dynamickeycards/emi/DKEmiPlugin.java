package com.mbx.dynamickeycards.emi;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.menu.BroadcastModeScreen;
import com.mbx.dynamickeycards.registry.DKBlocks;
import com.mbx.dynamickeycards.registry.DKItems;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.ArrayList;
import java.util.List;

/**
 * EMI integration: shows the card-machine processes (registering a blank card into a
 * keycard, and the duplicator's fork / member / co-manager outputs) as recipe
 * categories, since these transformations happen through block interaction rather than
 * a crafting table. Loaded only when EMI is installed.
 *
 * <p>Every recipe is generated per color so that each colored keycard / member / manager
 * card shows up with its own entry when looked up in EMI — not just the white ones.
 */
@EmiEntrypoint
public class DKEmiPlugin implements EmiPlugin {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, path);
    }

    public static final EmiRecipeCategory REGISTERING =
            new EmiRecipeCategory(id("registering"), EmiStack.of(DKBlocks.INSERT_CARD_READER.get()));
    public static final EmiRecipeCategory DUPLICATING =
            new EmiRecipeCategory(id("duplicating"), EmiStack.of(DKBlocks.CARD_DUPLICATOR.get()));

    @Override
    public void register(EmiRegistry registry) {
        // otherwise EMI's item panel draws its stacks right over the broadcast-mode screen's
        // device icon (which sits outside the screen's own background rectangle, next to the
        // arrow)
        registry.addExclusionArea(BroadcastModeScreen.class, (screen, consumer) -> {
            int[] bounds = screen.getDeviceIconScreenBounds();
            consumer.accept(new Bounds(bounds[0], bounds[1], bounds[2], bounds[3]));
        });

        registry.addCategory(REGISTERING);
        registry.addCategory(DUPLICATING);

        EmiIngredient readers = EmiIngredient.of(List.of(
                EmiStack.of(DKBlocks.INSERT_CARD_READER.get()),
                EmiStack.of(DKBlocks.TOUCH_CARD_READER.get()),
                EmiStack.of(DKBlocks.SWIPE_CARD_READER.get()),
                EmiStack.of(DKBlocks.ADVANCED_CARD_READER.get()),
                EmiStack.of(DKBlocks.OBSIDIAN_CARD_READER.get())));
        EmiStack duplicator = EmiStack.of(DKBlocks.CARD_DUPLICATOR.get());

        // any reader / the duplicator can be used to view their categories
        registry.addWorkstation(REGISTERING, readers);
        registry.addWorkstation(DUPLICATING, duplicator);

        EmiIngredient anyKeycard = ingredientOf(DKItems.KEYCARDS);
        EmiIngredient anyManager = ingredientOf(DKItems.MANAGER_CARDS);

        for (int i = 0; i < DKItems.COLORS.size(); i++) {
            String color = DKItems.COLORS.get(i);
            EmiStack blank = EmiStack.of(DKItems.BLANK_CARDS.get(i).get());
            EmiStack keycard = EmiStack.of(DKItems.KEYCARDS.get(i).get());
            EmiStack member = EmiStack.of(DKItems.MEMBER_CARDS.get(i).get());
            EmiStack manager = EmiStack.of(DKItems.MANAGER_CARDS.get(i).get());

            // Registering: a blank card becomes the same-color keycard on a reader — and an
            // already keyed keycard can be registered again on other readers, so the input
            // slot cycles between the blank card and the keycard.
            registry.addRecipe(new DKMachineRecipe(REGISTERING, id("registering/" + color),
                    List.of(EmiIngredient.of(List.of(blank, keycard))), readers, keycard));

            // Duplicating (fork): source keycard + a blank card of this color -> a copy of
            // this color that inherits what the source could open.
            registry.addRecipe(new DKMachineRecipe(DUPLICATING, id("duplicating/fork/" + color),
                    List.of(anyKeycard, blank), duplicator, keycard));

            // Issue member: a manager card + a blank card of this color -> a member card.
            registry.addRecipe(new DKMachineRecipe(DUPLICATING, id("duplicating/member/" + color),
                    List.of(anyManager, blank), duplicator, member));

            // Co-manager: a manager card + a blank (unregistered) manager card of this color
            // -> a second manager sharing the crew's key.
            registry.addRecipe(new DKMachineRecipe(DUPLICATING, id("duplicating/co_manager/" + color),
                    List.of(anyManager, manager), duplicator, manager));
        }
    }

    private static EmiIngredient ingredientOf(List<DeferredItem<net.minecraft.world.item.Item>> items) {
        List<EmiStack> stacks = new ArrayList<>();
        for (DeferredItem<net.minecraft.world.item.Item> item : items) {
            stacks.add(EmiStack.of(item.get()));
        }
        return EmiIngredient.of(stacks);
    }
}
