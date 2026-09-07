package com.mbx.dynamickeycards.compat.curios;

import com.mbx.dynamickeycards.client.KeycardNecklaceLayer;
import com.mbx.dynamickeycards.registry.DKItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

/**
 * Points Curios at {@link KeycardNecklaceRenderer} for every wearable card.
 *
 * <p>All 66 share one texture: what hangs on the lanyard reads as a generic ID badge at body
 * scale, where a card's own colour would be a couple of pixels. Per-card art would mean 66
 * textures for a distinction nobody can see from outside first person.
 *
 * <p>Reached only through {@link CuriosAvailability#isLoaded()} - see {@code DKClientSetup}.
 */
public final class CuriosClientSetup {

    private CuriosClientSetup() {
    }

    public static void registerRenderers() {
        ResourceLocation texture = KeycardNecklaceLayer.texture("keycard_necklace");
        for (Item card : DKItems.wearableCards()) {
            CuriosRendererRegistry.register(card, () -> new KeycardNecklaceRenderer(texture));
        }
    }
}
