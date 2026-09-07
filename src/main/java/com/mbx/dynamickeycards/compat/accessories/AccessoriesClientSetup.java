package com.mbx.dynamickeycards.compat.accessories;

import com.mbx.dynamickeycards.client.KeycardNecklaceLayer;
import com.mbx.dynamickeycards.registry.DKItems;
import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Points Accessories at {@link KeycardNecklaceRenderer} for every wearable card, sharing the one
 * texture the Curios side uses.
 *
 * <p>Reached only through {@link AccessoriesAvailability#isLoaded()} - see {@code DKClientSetup}.
 */
public final class AccessoriesClientSetup {

    private AccessoriesClientSetup() {
    }

    public static void registerRenderers() {
        ResourceLocation texture = KeycardNecklaceLayer.texture("keycard_necklace");
        for (Item card : DKItems.wearableCards()) {
            AccessoriesRendererRegistry.registerRenderer(card, () -> new KeycardNecklaceRenderer(texture));
        }
    }
}
