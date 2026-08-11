package com.mbx.dynamickeycards;

import com.mbx.dynamickeycards.block.CardReaderBlock;
import com.mbx.dynamickeycards.block.MotionSensorBlock;
import com.mbx.dynamickeycards.client.BoxRenderUtil;
import com.mbx.dynamickeycards.item.BoundSensorBlockItem;
import com.mbx.dynamickeycards.item.LinkedReaderBlockItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import org.jetbrains.annotations.Nullable;

/**
 * Highlights the reader or sensor a held {@link BoundSensorBlockItem} or
 * {@link LinkedReaderBlockItem} is set to connect with:
 *
 * <ul>
 *   <li>Shown every frame regardless of where the player is looking, for the specific target
 *   block only, out to 64 blocks - refreshed every tick the item is held.</li>
 *   <li><b>Edges only, no face fill.</b> Just the 12 edges of the reader's own selection shape.</li>
 *   <li>The 12 edges are real solid geometry, not thin {@code GL_LINES} - each one an actual
 *   6-faced cuboid, drawn opaque and lit ({@code DefaultVertexFormat.NEW_ENTITY} +
 *   {@code RENDERTYPE_ENTITY_SOLID_SHADER}, real lightmap/overlay - see {@link #EDGE_LIT}). Being
 *   opaque means overlapping geometry at the 12 edges' shared corners doesn't double-blend into a
 *   visible seam the way translucent quads would - only the shrinking width (see below) carries
 *   the fade.</li>
 *   <li>Every edge cuboid uses the same constant {@code (0,1,0)} normal rather than its true
 *   geometric one, so the whole box reads as one uniformly-lit surface rather than
 *   individually-shaded edges - flat and even under both vanilla lighting and a shader pack, by
 *   design.</li>
 *   <li>Not shown until refreshed - no fade-<em>in</em>. Once no longer refreshed (item put
 *   away), the highlight stays alive for {@link #FADE_TICKS} more ticks, with the alpha each
 *   frame computed as {@code lerp(prevTick, curTick)³} and rendering stopping entirely once that
 *   drops below 1/8. The line width scales by that same cubed alpha, so the shrink is slow and
 *   gradual at first (cubing a value near 1 barely moves it) and rapid at the very end - the
 *   width is still 1/8 of full (a real, visible thin line, not a sliver) at the instant the hard
 *   cutoff removes it, so it reads as "shrinks to a hairline, then snaps away" rather than fading
 *   smoothly to nothing. A plain linear width curve was tried first and looked wrong: linear
 *   spends an *even* amount of time at every width, including the imperceptibly-thin ones near
 *   zero, so it visually "gives up" earlier and less decisively than this cubic-then-hard-cutoff
 *   shape does.</li>
 * </ul>
 */
@EventBusSubscriber(modid = DynamicKeycards.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class DKClientEvents {

    /** The two colors the highlight alternates between. */
    private static final int COLOR_A = 0x708DAD;
    private static final int COLOR_B = 0x90ADCD;
    private static final double MAX_RANGE = 64.0;
    private static final float LINE_WIDTH = 1 / 32f;
    private static final int FADE_TICKS = 8;
    private static final int TTL = 2;
    /** Below this alpha, stop rendering entirely - see the class doc for the fade curve this pairs with. */
    private static final float ALPHA_CUTOFF = 1 / 8f;
    /**
     * Insets the shape AABB by 1/128, shrinking it slightly *into* the block, before a second,
     * camera-relative inflate (below) is applied on top - by the same 1/128, in the opposite
     * direction, whenever the camera is outside the box (the ordinary case). The two cancel out
     * exactly, so the line lands flush on the true shape boundary; the one case that doesn't
     * cancel is the camera clipped *inside* the box, where the second inflate deflates further
     * inward instead, keeping the highlight visible without the camera poking through opaque
     * geometry.
     */
    private static final double CALLER_INFLATE = -0.0078125;
    /** The camera-relative inflate magnitude - see {@link #CALLER_INFLATE}. */
    private static final float CAMERA_RELATIVE_INFLATE = 1 / 128f;

    private static final RenderType EDGE_LIT =
            BoxRenderUtil.opaqueLitBoxType("dynamickeycards:sensor_outline_edges", BoxRenderUtil.WHITE_TEXTURE);

    @Nullable
    private static BlockPos highlightTarget;
    private static int ticksTillRemoval = -FADE_TICKS - 1;

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        BlockPos target = null;
        if (player != null && minecraft.level != null) {
            BlockPos bound = highlightTargetOf(player);
            if (bound != null && player.canInteractWithBlock(bound, MAX_RANGE)
                    && isHighlightable(minecraft.level.getBlockState(bound).getBlock())) {
                target = bound;
            }
        }
        if (target != null) {
            highlightTarget = target;
            ticksTillRemoval = TTL;
        } else {
            ticksTillRemoval--;
            if (ticksTillRemoval < -FADE_TICKS) {
                highlightTarget = null;
            }
        }
    }

    @SubscribeEvent
    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || highlightTarget == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        BlockState readerState = minecraft.level.getBlockState(highlightTarget);
        if (!isHighlightable(readerState.getBlock())) {
            return;
        }
        VoxelShape shape = readerState.getShape(minecraft.level, highlightTarget);
        if (shape.isEmpty()) {
            return;
        }

        float alpha = currentAlpha(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (alpha < ALPHA_CUTOFF) {
            return;
        }

        int color = (event.getRenderTick() % 16 < 8) ? COLOR_A : COLOR_B;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose pose = poseStack.last();

        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer edgeConsumer = buffer.getBuffer(EDGE_LIT);
        float lineWidth = LINE_WIDTH * alpha;
        // The overall bounding envelope, not shape.toAabbs()'s exact constituent boxes - a
        // ceiling sensor's real shape is 11 separate boxes (a thin rim plus a raised interior),
        // which traced a jagged multi-box outline instead of one clean box. Every other
        // highlightable block's shape is already a single box, so this is a no-op difference for
        // them - bounds() of one box is that box.
        AABB box = shape.bounds().inflate(CALLER_INFLATE).move(highlightTarget);
        boolean cameraInside = box.contains(cam);
        box = box.inflate(cameraInside ? -CAMERA_RELATIVE_INFLATE : CAMERA_RELATIVE_INFLATE);
        BoxRenderUtil.renderThickBoxEdges(pose, edgeConsumer, box, lineWidth, r, g, b, alpha);
        buffer.endBatch(EDGE_LIT);

        poseStack.popPose();
    }

    /** {@code alpha = lerp(prevTick, curTick)³} - see the class doc for why cubic. */
    private static float currentAlpha(float partialTick) {
        if (ticksTillRemoval >= 0) {
            return 1f;
        }
        int prevTicks = ticksTillRemoval + 1;
        float lastAlpha = prevTicks >= 0 ? 1f : 1f + (prevTicks / (float) FADE_TICKS);
        float thisAlpha = 1f + (ticksTillRemoval / (float) FADE_TICKS);
        float alpha = Mth.lerp(partialTick, lastAlpha, thisAlpha);
        return alpha * alpha * alpha;
    }

    @Nullable
    private static BlockPos highlightTargetOf(LocalPlayer player) {
        BlockPos main = targetOf(player.getMainHandItem());
        if (main != null) {
            return main;
        }
        return targetOf(player.getOffhandItem());
    }

    /**
     * The reader or sensor a held {@link BoundSensorBlockItem} or {@link LinkedReaderBlockItem}
     * is set to connect with, if any.
     */
    @Nullable
    private static BlockPos targetOf(ItemStack stack) {
        if (stack.getItem() instanceof BoundSensorBlockItem) {
            BlockPos boundReader = BoundSensorBlockItem.boundReader(stack);
            return boundReader != null ? boundReader : BoundSensorBlockItem.boundSensor(stack);
        }
        if (stack.getItem() instanceof LinkedReaderBlockItem) {
            return LinkedReaderBlockItem.linkedReader(stack);
        }
        return null;
    }

    /** Whether {@code block} is a valid highlight target: a card reader, or either kind of motion sensor. */
    private static boolean isHighlightable(Block block) {
        return block instanceof CardReaderBlock || block instanceof MotionSensorBlock;
    }
}
