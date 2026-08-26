package com.mbx.dynamickeycards.recipe;

import com.mbx.dynamickeycards.registry.DKComponents;
import com.mbx.dynamickeycards.registry.DKRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.CraftingInput;

import java.util.UUID;

/**
 * A shapeless recipe that carries {@link DKComponents#BOUND_OWNER} from its input to its result -
 * used for the keycard/maintenance-card swaps, which exchange one tier of a card for the other.
 *
 * <p>Plain {@code minecraft:crafting_shapeless} cannot be used for those: its {@code assemble}
 * returns {@code this.result.copy()}, so every component on the input is discarded. For an
 * ordinary recipe that's fine, but an estate card's binding is meant to be <em>permanent</em>
 * (see {@code OwnerBoundCard#activate} - an already-bound card silently refuses to re-bind), and
 * a component-dropping swap would quietly undo exactly that: swap a bound card to the other tier
 * and back, and it comes out unbound and re-bindable by anyone. That turns a stolen estate card
 * into a freely re-claimable one, defeating the binding entirely.
 *
 * <p>So this preserves the binding instead - which is also simply what a player would expect:
 * changing which tier your card operates on shouldn't change whose card it is. The golden pair
 * carries no binding at all, so for those this behaves exactly like a plain shapeless recipe;
 * they use this type anyway so "swapping tiers keeps the card yours" is one rule rather than a
 * per-card exception.
 */
public class MaintenanceCardSwapRecipe extends ShapelessRecipe {

    private final ItemStack result;

    public MaintenanceCardSwapRecipe(String group, CraftingBookCategory category, ItemStack result,
                                     NonNullList<Ingredient> ingredients) {
        super(group, category, result, ingredients);
        this.result = result;
    }

    /**
     * The plain shapeless result, plus the bound owner of whichever input actually carried one.
     * Scans every slot rather than assuming slot 0 - these recipes have a single ingredient, but
     * the crafting grid it sits in may place it anywhere.
     */
    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack assembled = super.assemble(input, registries);
        for (int i = 0; i < input.size(); i++) {
            UUID owner = input.getItem(i).get(DKComponents.BOUND_OWNER.get());
            if (owner != null) {
                assembled.set(DKComponents.BOUND_OWNER.get(), owner);
                break;
            }
        }
        return assembled;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return DKRecipes.MAINTENANCE_CARD_SWAP.get();
    }

    /**
     * Mirrors {@code ShapelessRecipe.Serializer}'s own codecs exactly - same JSON shape, same
     * wire format, only building this subclass instead. Kept deliberately parallel so a recipe
     * file differs from a vanilla shapeless one by its {@code type} line alone.
     */
    public static class Serializer implements RecipeSerializer<MaintenanceCardSwapRecipe> {

        private static final MapCodec<MaintenanceCardSwapRecipe> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                                Codec.STRING.optionalFieldOf("group", "").forGetter(MaintenanceCardSwapRecipe::getGroup),
                                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC)
                                        .forGetter(MaintenanceCardSwapRecipe::category),
                                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
                                Ingredient.CODEC_NONEMPTY
                                        .listOf()
                                        .fieldOf("ingredients")
                                        .flatXmap(
                                                list -> {
                                                    Ingredient[] ingredients = list.stream()
                                                            .filter(ingredient -> !ingredient.isEmpty())
                                                            .toArray(Ingredient[]::new);
                                                    if (ingredients.length == 0) {
                                                        return DataResult.error(() -> "No ingredients for shapeless recipe");
                                                    }
                                                    return ingredients.length > 9
                                                            ? DataResult.error(() -> "Too many ingredients for shapeless recipe")
                                                            : DataResult.success(NonNullList.of(Ingredient.EMPTY, ingredients));
                                                },
                                                DataResult::success
                                        )
                                        .forGetter(MaintenanceCardSwapRecipe::getIngredients)
                        )
                        .apply(instance, MaintenanceCardSwapRecipe::new)
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, MaintenanceCardSwapRecipe> STREAM_CODEC =
                StreamCodec.of(Serializer::toNetwork, Serializer::fromNetwork);

        @Override
        public MapCodec<MaintenanceCardSwapRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MaintenanceCardSwapRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static MaintenanceCardSwapRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            String group = buffer.readUtf();
            CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
            int size = buffer.readVarInt();
            NonNullList<Ingredient> ingredients = NonNullList.withSize(size, Ingredient.EMPTY);
            ingredients.replaceAll(ignored -> Ingredient.CONTENTS_STREAM_CODEC.decode(buffer));
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            return new MaintenanceCardSwapRecipe(group, category, result, ingredients);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, MaintenanceCardSwapRecipe recipe) {
            buffer.writeUtf(recipe.getGroup());
            buffer.writeEnum(recipe.category());
            buffer.writeVarInt(recipe.getIngredients().size());
            for (Ingredient ingredient : recipe.getIngredients()) {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient);
            }
            ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
        }
    }
}
