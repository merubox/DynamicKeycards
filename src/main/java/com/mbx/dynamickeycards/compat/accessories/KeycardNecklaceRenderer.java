package com.mbx.dynamickeycards.compat.accessories;

import com.mbx.dynamickeycards.client.KeycardNecklaceRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wispforest.accessories.api.client.AccessoryRenderer;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Accessories' way in to {@link KeycardNecklaceRendering} - the counterpart to the Curios
 * renderer, differing only in this mod's signature and how it copies the wearer's pose.
 *
 * <p>Accessories hands over the entity model directly and it isn't always humanoid, so the pose
 * copy is skipped for other shapes. The scale correction is not: it exists because of
 * {@code SCALE}, not because of the pose, and skipping it there is how the two mods drifted apart
 * once already.
 */
public class KeycardNecklaceRenderer implements AccessoryRenderer {

    private final ResourceLocation texture;
    private final KeycardNecklaceRendering.LazyModel model = new KeycardNecklaceRendering.LazyModel();

    public KeycardNecklaceRenderer(ResourceLocation texture) {
        this.texture = texture;
    }

    @Override
    public <M extends LivingEntity> void render(
            ItemStack stack, SlotReference reference, PoseStack poseStack,
            EntityModel<M> entityModel, MultiBufferSource bufferSource, int light,
            float limbSwing, float limbSwingAmount, float partialTicks,
            float ageInTicks, float netHeadYaw, float headPitch) {
        LivingEntity entity = reference.entity();
        if (entity == null) {
            return;
        }
        KeycardNecklaceRendering.draw(model.get(), texture, stack, entity, poseStack, bufferSource,
                light, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch,
                () -> {
                    if (entityModel instanceof HumanoidModel<?>) {
                        AccessoryRenderer.followBodyRotations(entity, model.get());
                    }
                });
    }
}
