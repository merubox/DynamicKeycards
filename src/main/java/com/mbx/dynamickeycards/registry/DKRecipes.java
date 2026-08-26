package com.mbx.dynamickeycards.registry;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.recipe.MaintenanceCardSwapRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** This mod's one custom recipe type - see {@link MaintenanceCardSwapRecipe} for why it exists. */
public class DKRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, DynamicKeycards.MOD_ID);

    public static final Supplier<RecipeSerializer<MaintenanceCardSwapRecipe>> MAINTENANCE_CARD_SWAP =
            RECIPE_SERIALIZERS.register("maintenance_card_swap", MaintenanceCardSwapRecipe.Serializer::new);
}
