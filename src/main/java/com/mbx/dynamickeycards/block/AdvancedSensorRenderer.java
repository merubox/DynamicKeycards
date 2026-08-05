package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Draws an {@link AdvancedSensorBlockEntity} itself - its blockstate model
 * ({@code empty_wall}/{@code empty_ceiling}) is deliberately empty. 17 dye-color looks (16 dyes
 * plus the native undyed "gold" one) times on/off times, for the wall variant, 4 facings would
 * balloon the blockstate variant count into the hundreds if done the ordinary way, so instead
 * each combination is a plain model registered as an "extra" (not tied to any blockstate) via
 * {@code DKClientSetup}, and this renderer just looks up and draws whichever one currently
 * matches the block entity's own {@link AdvancedSensorBlockEntity#getAccentColor()}.
 */
public class AdvancedSensorRenderer implements BlockEntityRenderer<AdvancedSensorBlockEntity> {

    public AdvancedSensorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AdvancedSensorBlockEntity sensor, float partialTick, PoseStack poseStack,
                        MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = sensor.getBlockState();
        if (!state.hasProperty(MotionSensorBlock.PRESENT)) {
            return;
        }
        boolean on = state.getValue(MotionSensorBlock.PRESENT);
        ResourceLocation location = modelLocation(state, sensor.getAccentColor(), on);

        ModelManager modelManager = Minecraft.getInstance().getModelManager();
        var model = modelManager.getModel(ModelResourceLocation.standalone(location));

        poseStack.pushPose();
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(), buffer.getBuffer(RenderType.cutout()), state, model,
                1f, 1f, 1f, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static ResourceLocation modelLocation(BlockState state, @Nullable DyeColor color, boolean on) {
        String stateStr = on ? "on" : "off";
        String colorSegment = color == null ? "" : color.getSerializedName() + "_";
        if (state.hasProperty(WallSensorBlock.FACING)) {
            String facing = state.getValue(WallSensorBlock.FACING).getSerializedName();
            return ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID,
                    "block/sensor/advanced_wall_sensor_" + colorSegment + stateStr + "_" + facing);
        }
        return ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID,
                "block/sensor/advanced_ceiling_sensor_" + colorSegment + stateStr);
    }
}
