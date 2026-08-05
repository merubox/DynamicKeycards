package com.mbx.dynamickeycards;

import com.mbx.dynamickeycards.block.CardReaderBlock;
import com.mbx.dynamickeycards.item.BoundSensorBlockItem;
import com.mbx.dynamickeycards.item.LinkedReaderBlockItem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
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
 * Highlights the reader a held {@link BoundSensorBlockItem} or {@link LinkedReaderBlockItem} is
 * set to connect with:
 *
 * <ul>
 *   <li>Shown every frame regardless of where the player is looking, for the specific target
 *   reader only, out to 64 blocks - refreshed every tick the item is held.</li>
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

    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/misc/white.png");

    /** Opaque, lit ({@code NEW_ENTITY} format + the vanilla entity-solid shader, real lightmap/overlay), textured with a blank white square. */
    private static final RenderType EDGE_LIT = RenderType.create(
            "dynamickeycards:sensor_outline_edges",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_SOLID_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(WHITE_TEXTURE, false, false))
                    .setCullState(RenderStateShard.CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(false)
    );

    @Nullable
    private static BlockPos highlightTarget;
    private static int ticksTillRemoval = -FADE_TICKS - 1;

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        BlockPos target = null;
        if (player != null && minecraft.level != null) {
            BlockPos bound = boundReaderPos(player);
            if (bound != null && player.canInteractWithBlock(bound, MAX_RANGE)
                    && minecraft.level.getBlockState(bound).getBlock() instanceof CardReaderBlock) {
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
        if (!(readerState.getBlock() instanceof CardReaderBlock)) {
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
        for (AABB localAabb : shape.toAabbs()) {
            AABB box = localAabb.inflate(CALLER_INFLATE).move(highlightTarget);
            boolean cameraInside = box.contains(cam);
            box = box.inflate(cameraInside ? -CAMERA_RELATIVE_INFLATE : CAMERA_RELATIVE_INFLATE);
            renderThickBoxEdges(pose, edgeConsumer, box, lineWidth, r, g, b, alpha);
        }
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
    private static BlockPos boundReaderPos(LocalPlayer player) {
        BlockPos main = targetOf(player.getMainHandItem());
        if (main != null) {
            return main;
        }
        return targetOf(player.getOffhandItem());
    }

    /** The reader a held {@link BoundSensorBlockItem} or {@link LinkedReaderBlockItem} is set to connect with, if any. */
    @Nullable
    private static BlockPos targetOf(ItemStack stack) {
        if (stack.getItem() instanceof BoundSensorBlockItem) {
            return BoundSensorBlockItem.boundReader(stack);
        }
        if (stack.getItem() instanceof LinkedReaderBlockItem) {
            return LinkedReaderBlockItem.linkedReader(stack);
        }
        return null;
    }

    /**
     * The 12 edges of {@code box}, each as its own thin solid cuboid: three from the min corner,
     * two each from three of the adjacent corners, one each from the remaining three.
     */
    private static void renderThickBoxEdges(PoseStack.Pose pose, VertexConsumer consumer, AABB box, float width,
                                             float r, float g, float b, float alpha) {
        float minX = (float) box.minX, minY = (float) box.minY, minZ = (float) box.minZ;
        float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;
        float lenX = maxX - minX, lenY = maxY - minY, lenZ = maxZ - minZ;

        bufferCuboidLine(pose, consumer, minX, minY, minZ, Direction.EAST, lenX, width, r, g, b, alpha);
        bufferCuboidLine(pose, consumer, minX, minY, minZ, Direction.UP, lenY, width, r, g, b, alpha);
        bufferCuboidLine(pose, consumer, minX, minY, minZ, Direction.SOUTH, lenZ, width, r, g, b, alpha);

        bufferCuboidLine(pose, consumer, maxX, minY, minZ, Direction.UP, lenY, width, r, g, b, alpha);
        bufferCuboidLine(pose, consumer, maxX, minY, minZ, Direction.SOUTH, lenZ, width, r, g, b, alpha);

        bufferCuboidLine(pose, consumer, minX, maxY, minZ, Direction.EAST, lenX, width, r, g, b, alpha);
        bufferCuboidLine(pose, consumer, minX, maxY, minZ, Direction.SOUTH, lenZ, width, r, g, b, alpha);

        bufferCuboidLine(pose, consumer, minX, minY, maxZ, Direction.EAST, lenX, width, r, g, b, alpha);
        bufferCuboidLine(pose, consumer, minX, minY, maxZ, Direction.UP, lenY, width, r, g, b, alpha);

        bufferCuboidLine(pose, consumer, minX, maxY, maxZ, Direction.EAST, lenX, width, r, g, b, alpha);
        bufferCuboidLine(pose, consumer, maxX, minY, maxZ, Direction.UP, lenY, width, r, g, b, alpha);
        bufferCuboidLine(pose, consumer, maxX, maxY, minZ, Direction.SOUTH, lenZ, width, r, g, b, alpha);
    }

    /** A thin solid cuboid of cross-section {@code width} running {@code length} from {@code (ox,oy,oz)} toward {@code direction}. */
    private static void bufferCuboidLine(PoseStack.Pose pose, VertexConsumer consumer, float ox, float oy, float oz,
                                          Direction direction, float length, float width,
                                          float r, float g, float b, float alpha) {
        float half = width / 2f;
        float minX = ox - half, minY = oy - half, minZ = oz - half;
        float maxX = ox + half, maxY = oy + half, maxZ = oz + half;
        switch (direction) {
            case DOWN -> minY -= length;
            case UP -> maxY += length;
            case NORTH -> minZ -= length;
            case SOUTH -> maxZ += length;
            case WEST -> minX -= length;
            case EAST -> maxX += length;
        }
        bufferCuboid(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, alpha);
    }

    /**
     * A solid, lit box - six real quads with correct outward winding (culled backface-out), but
     * every vertex gets the same fake {@code (0,1,0)} normal rather than its true one - see the
     * class doc. The vertex color's alpha channel carries the fade value too: meaningless for
     * vanilla's own opaque blending, but under a shader pack the replacement fragment shader
     * still receives it and, on at least Bliss/Complementary Reimagined, visibly uses it - an
     * earlier version that hardcoded this to 1 stayed pure white right up until the hard cutoff
     * under those two, instead of darkening as it shrinks.
     */
    private static void bufferCuboid(PoseStack.Pose pose, VertexConsumer consumer,
                                      float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                                      float r, float g, float b, float alpha) {
        litQuad(pose, consumer, minX, minY, maxZ, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, alpha); // down
        litQuad(pose, consumer, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, alpha); // up
        litQuad(pose, consumer, maxX, maxY, minZ, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, r, g, b, alpha); // north
        litQuad(pose, consumer, minX, maxY, maxZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, alpha); // south
        litQuad(pose, consumer, minX, maxY, minZ, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, alpha); // west
        litQuad(pose, consumer, maxX, maxY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, alpha); // east
    }

    /** Full {@code NEW_ENTITY} quad (uv/overlay/lightmap/normal) with the shared fake "up" normal, for {@link #EDGE_LIT}. */
    private static void litQuad(PoseStack.Pose pose, VertexConsumer consumer,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 float x2, float y2, float z2, float x3, float y3, float z3,
                                 float r, float g, float b, float alpha) {
        int light = LightTexture.FULL_BRIGHT;
        consumer.addVertex(pose, x0, y0, z0).setColor(r, g, b, alpha).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, alpha).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, alpha).setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, x3, y3, z3).setColor(r, g, b, alpha).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
    }
}
