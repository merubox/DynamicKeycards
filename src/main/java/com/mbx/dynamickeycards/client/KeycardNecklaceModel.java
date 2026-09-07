package com.mbx.dynamickeycards.client;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Keycard necklace geometry - a lanyard on the torso with a clip and a card hanging from it.
 *
 * <p>Everything is modelled at 2x scale and rendered at 0.51, which buys half-pixel geometry
 * precision and double texture density. The 0.51 (rather than 0.50) also inflates the torso box
 * ~0.33px past the player body: outside the skin's outer layer, inside armour.
 *
 * <p>Texture is 64x48. The torso box reserves u0-49 / v0-33 and carries the lanyard; the two
 * pendant cubes sit in the free band at v35+.
 *
 * <p>Pure vanilla - deliberately no accessory-mod types here, so it links whether or not one is
 * installed. The mod-specific renderer that draws it lives in {@code compat/}.
 */
public class KeycardNecklaceModel extends HumanoidModel<LivingEntity> {

    /**
     * Modelled at 2x, drawn at this. The 0.51 rather than 0.50 also inflates the torso box
     * ~0.33px past the player body: outside the skin's outer layer, inside armour.
     */
    private static final float SCALE = 0.51F;

    public KeycardNecklaceModel(ModelPart part) {
        super(part, RenderType::entityTranslucent);
    }

    @Override
    protected Iterable<ModelPart> headParts() {
        return ImmutableList.of();
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(body);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
        poseStack.pushPose();
        if (this.young) {
            float babyBodyScale = 2;
            float scale = 1 / babyBodyScale;
            poseStack.scale(scale, scale, scale);
            float bodyYOffset = 24;
            poseStack.translate(0, bodyYOffset / 16, 0);
        }
        poseStack.scale(SCALE, SCALE, SCALE);
        this.bodyParts().forEach(part -> part.render(poseStack, buffer, light, overlay, color));
        poseStack.popPose();
    }

    /**
     * Adds back the part of the torso's own offset that {@link #SCALE} eats.
     *
     * <p>{@code copyPropertiesTo} - what both mods' {@code followBodyRotations} calls - already
     * brings the body part's position across, crouching included (vanilla sets
     * {@code body.y = 3.2} there). But that offset is applied <em>inside</em> the scaled pose, so
     * it lands at {@code 3.2 * SCALE} instead of {@code 3.2}: the necklace drops only about half
     * as far as the torso it hangs on.
     *
     * <p>So the correction is the shortfall, {@code offset * (1 - SCALE)}, not a fixed crouch
     * nudge - deriving it from the copied position keeps it right if either the pose or the scale
     * changes, and it is exactly zero while standing, where {@code body.y} is zero.
     *
     * <p>Curios ships a fixed-value {@code ICurioRenderer.translateIfSneaking} for renderers that
     * don't copy the body pose at all; ours does, so using that on top would double-correct.
     * Accessories has no equivalent either way, so both renderers call this instead - one
     * behaviour for both mods rather than one that silently differs.
     */
    public void compensateScaledBodyOffset(PoseStack poseStack) {
        float shortfall = 1.0F - SCALE;
        poseStack.translate(this.body.x / 16.0F * shortfall,
                this.body.y / 16.0F * shortfall,
                this.body.z / 16.0F * shortfall);
    }

    /**
     * Wraps pendant cubes in the torso box that carries the lanyard texture.
     * Width 16 (even) so a 6-wide pendant centres exactly on X.
     */
    private static MeshDefinition createNecklace(CubeListBuilder body) {
        MeshDefinition mesh = createMesh(CubeDeformation.NONE, 0);

        mesh.getRoot().addOrReplaceChild(
                "body",
                body.texOffs(0, 0)
                        .addBox(-(2 * 8) / 2F, -1 / 2F, -(2 * 4 + 1) / 2F, 2 * 8, 2 * 12 + 1, 2 * 4 + 1),
                PartPose.ZERO
        );

        return mesh;
    }

    public static MeshDefinition createKeycard() {
        CubeListBuilder body = CubeListBuilder.create();

        // Clip: full thickness. Z -5.0 .. -4.0, so half of it sits inside the torso box and half
        // protrudes.
        body.texOffs(0, 35);
        body.addBox(-3, 8.5F, -5, 6, 1, 1);

        // Card: half thickness, same nominal Z origin as the clip. Declared as depth 1 so the UV
        // footprint stays on whole texels (a fractional depth would offset the front face by half
        // a texel and smear the pixel art); CubeDeformation then shaves 0.25 off each Z face
        // without touching UV. Result: Z -4.75 .. -4.25, entirely within the clip's depth, so the
        // card never protrudes past it and no face is coplanar with the torso surface
        // (entityTranslucent does not cull, so coplanar faces z-fight).
        body.texOffs(16, 35);
        body.addBox(-3, 9.5F, -5, 6, 3, 1, new CubeDeformation(0, 0, -0.25F));

        return createNecklace(body);
    }
}
