package com.mbx.dynamickeycards.client;

import com.mbx.dynamickeycards.DynamicKeycards;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The baked-model half of the worn keycard: the layer location and its definition, both pure
 * vanilla. Registered unconditionally - a layer nothing draws costs nothing, and keeping it out
 * of {@code compat/} means no accessory mod has to be present for this class to link.
 */
public final class KeycardNecklaceLayer {

    public static final ModelLayerLocation LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "keycard_necklace"), "main");

    private KeycardNecklaceLayer() {
    }

    /** 64x48 must match the texture size or every UV shifts. */
    public static void register(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LOCATION,
                () -> LayerDefinition.create(KeycardNecklaceModel.createKeycard(), 64, 48));
    }

    /** Texture at {@code assets/dynamickeycards/textures/entity/wearable/<name>.png}. */
    public static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                DynamicKeycards.MOD_ID, "textures/entity/wearable/" + name + ".png");
    }
}
