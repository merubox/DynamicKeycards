package com.mbx.dynamickeycards.compat.curios;

import com.mbx.dynamickeycards.client.KeycardNecklaceRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Curios' way in to {@link KeycardNecklaceRendering} - a signature and one call, with the pose
 * copy passed along as the one step that is Curios' own.
 *
 * <p>Lives here rather than in {@code client/} because it names Curios types: reaching this class
 * links it, and with Curios absent that would throw. Only {@link CuriosClientSetup} touches it.
 */
public class KeycardNecklaceRenderer implements ICurioRenderer {

    private final ResourceLocation texture;
    private final KeycardNecklaceRendering.LazyModel model = new KeycardNecklaceRendering.LazyModel();

    public KeycardNecklaceRenderer(ResourceLocation texture) {
        this.texture = texture;
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(
            ItemStack stack, SlotContext slotContext, PoseStack poseStack,
            RenderLayerParent<T, M> renderLayerParent, MultiBufferSource bufferSource,
            int light, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        LivingEntity entity = slotContext.entity();
        if (entity == null) {
            return;
        }
        KeycardNecklaceRendering.draw(model.get(), texture, stack, entity, poseStack, bufferSource,
                light, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch,
                () -> ICurioRenderer.followBodyRotations(entity, model.get()));
    }
}
