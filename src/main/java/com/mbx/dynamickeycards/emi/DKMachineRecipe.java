package com.mbx.dynamickeycards.emi;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A machine "recipe" for EMI that mimics Create's process display: one or two input
 * slots on the left, the machine block shown as a catalyst in the middle (hover it for
 * its name), an arrow, and the produced card on the right. These aren't real crafting
 * recipes — they document what registering/duplicating a card produces.
 */
public class DKMachineRecipe extends BasicEmiRecipe {

    private final List<EmiIngredient> inputSlots;
    private final EmiIngredient machine;
    private final EmiStack result;

    public DKMachineRecipe(EmiRecipeCategory category, ResourceLocation id,
                           List<EmiIngredient> inputSlots, EmiIngredient machine, EmiStack result) {
        super(category, id, width(inputSlots.size()), 18);
        this.inputSlots = inputSlots;
        this.machine = machine;
        this.result = result;
        this.inputs.addAll(inputSlots);
        this.inputs.add(machine);
        this.outputs.add(result);
    }

    private static int width(int inputCount) {
        // inputs (18 each) | +4 machine (18) | +6 arrow (24) | +6 output (18)
        return inputCount * 18 + 4 + 18 + 6 + 24 + 6 + 18;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        int x = 0;
        for (EmiIngredient in : inputSlots) {
            widgets.addSlot(in, x, 0);
            x += 18;
        }
        x += 4;
        // the machine block: a catalyst slot (not consumed), hover shows its name
        widgets.addSlot(machine, x, 0).catalyst(true);
        x += 18 + 6;
        widgets.addTexture(EmiTexture.EMPTY_ARROW, x, 1);
        x += 24 + 6;
        widgets.addSlot(result, x, 0).recipeContext(this);
    }
}
