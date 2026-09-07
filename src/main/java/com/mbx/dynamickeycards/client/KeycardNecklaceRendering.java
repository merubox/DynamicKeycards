package com.mbx.dynamickeycards.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Everything about drawing a worn keycard that isn't specific to an accessory mod.
 *
 * <p>Curios and Accessories hand a renderer entirely different arguments - a {@code SlotContext}
 * and a {@code RenderLayerParent} against a {@code SlotReference} and the entity model - so each
 * needs its own class implementing its own interface. What those classes must not each own is
 * <em>what gets drawn</em>: when the two held a copy of it apiece, a fix landed in one and not
 * the other and the mods silently diverged.
 *
 * <p>So the mod-specific classes are reduced to a signature and one call. The only step that
 * genuinely differs, copying the wearer's body pose, is passed in as {@code followBodyRotations}.
 */
public final class KeycardNecklaceRendering {

    private KeycardNecklaceRendering() {
    }

    /**
     * Bakes on first use - the model set isn't ready while renderers are being registered, so a
     * renderer can hold one of these and let it resolve the first time it actually draws.
     */
    public static final class LazyModel {

        private KeycardNecklaceModel model;

        public KeycardNecklaceModel get() {
            if (model == null) {
                model = new KeycardNecklaceModel(
                        Minecraft.getInstance().getEntityModels().bakeLayer(KeycardNecklaceLayer.LOCATION));
            }
            return model;
        }
    }

    /**
     * @param followBodyRotations each mod's own way of copying the wearer's pose onto the model;
     *                            run before the scale correction, which depends on the pose it sets
     */
    public static void draw(KeycardNecklaceModel model, ResourceLocation texture, ItemStack stack,
                            LivingEntity entity, PoseStack poseStack, MultiBufferSource bufferSource,
                            int light, float limbSwing, float limbSwingAmount, float partialTicks,
                            float ageInTicks, float netHeadYaw, float headPitch,
                            Runnable followBodyRotations) {
        model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTicks);
        model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        followBodyRotations.run();
        model.compensateScaledBodyOffset(poseStack);

        VertexConsumer consumer = ItemRenderer.getFoilBuffer(
                bufferSource, model.renderType(texture), false, stack.hasFoil());
        model.renderToBuffer(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
    }
}
