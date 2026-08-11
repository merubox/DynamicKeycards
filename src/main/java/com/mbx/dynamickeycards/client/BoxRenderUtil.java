package com.mbx.dynamickeycards.client;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Shared solid-lit-cuboid box rendering, used by every world-space highlight box this mod draws
 * (the reader/sensor connection-target outline in {@code DKClientEvents}, and the sensor
 * detection-range highlight in {@code SensorRangeClientHandler}) - factored out once both had
 * grown byte-for-byte identical copies of the same vertex-buffering code, each edited to fix the
 * same kind of bug independently of the other.
 */
public final class BoxRenderUtil {

    private BoxRenderUtil() {
    }

    /** A blank white square - every solid-color (non-{@link #litQuad} with real UVs) box uses this. */
    public static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/misc/white.png");

    /**
     * An opaque, lit ({@code NEW_ENTITY} format + the vanilla entity-solid shader, real
     * lightmap/overlay) {@link RenderType} textured with {@code texture} - {@code name} only
     * affects the debug/profiling label, so each caller can keep its own even when the rest of
     * the composite state is identical.
     */
    public static RenderType opaqueLitBoxType(String name, ResourceLocation texture) {
        return RenderType.create(
                name,
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_SOLID_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setCullState(RenderStateShard.CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .createCompositeState(false)
        );
    }

    /**
     * The 12 edges of {@code box}, each as its own thin solid cuboid: three from the min corner,
     * two each from three of the adjacent corners, one each from the remaining three. Solid
     * color - see {@link #litQuad} if a caller needs a single real-UV textured quad instead (for
     * a face fill).
     */
    public static void renderThickBoxEdges(PoseStack.Pose pose, VertexConsumer consumer, AABB box, float width,
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
     * every vertex gets the same fake {@code (0,1,0)} normal rather than its true one, so the
     * whole box reads as one uniformly-lit surface rather than individually-shaded edges - flat
     * and even under both vanilla lighting and a shader pack, by design. Solid color (UV
     * {@code 0..1}, meaningless against a blank white texture) - see {@link #litQuad} for a
     * single real-UV quad instead.
     */
    private static void bufferCuboid(PoseStack.Pose pose, VertexConsumer consumer,
                                      float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                                      float r, float g, float b, float alpha) {
        litQuad(pose, consumer, minX, minY, maxZ, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, 1, 1, r, g, b, alpha);
        litQuad(pose, consumer, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, 1, 1, r, g, b, alpha);
        litQuad(pose, consumer, maxX, maxY, minZ, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, 1, 1, r, g, b, alpha);
        litQuad(pose, consumer, minX, maxY, maxZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, 1, 1, r, g, b, alpha);
        litQuad(pose, consumer, minX, maxY, minZ, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, 1, 1, r, g, b, alpha);
        litQuad(pose, consumer, maxX, maxY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, 1, 1, r, g, b, alpha);
    }

    /**
     * Full {@code NEW_ENTITY} quad (uv/overlay/lightmap/normal) with the shared fake "up" normal
     * (see {@link #bufferCuboid}'s own doc for why). {@code uSize}/{@code vSize} are texture-space
     * extents, not normalized {@code 0..1} - pass {@code 1,1} for an ordinary solid-color quad
     * against a blank texture, or a face's actual width/height in blocks to tile a repeating
     * texture across it (relies on that texture's own default GL_REPEAT wrap - see
     * {@code SensorRangeClientHandler}'s stripe texture for the case this exists for). The vertex
     * color's alpha channel carries the fade value too: meaningless for vanilla's own opaque
     * blending on an opaque {@link RenderType}, but under a shader pack the replacement fragment
     * shader still receives it and, on at least Bliss/Complementary Reimagined, visibly uses it -
     * an earlier version that hardcoded this to 1 stayed pure white right up until a hard alpha
     * cutoff removed it entirely under those two, instead of darkening as it faded.
     */
    public static void litQuad(PoseStack.Pose pose, VertexConsumer consumer,
                                float x0, float y0, float z0, float x1, float y1, float z1,
                                float x2, float y2, float z2, float x3, float y3, float z3,
                                float uSize, float vSize, float r, float g, float b, float alpha) {
        int light = LightTexture.FULL_BRIGHT;
        consumer.addVertex(pose, x0, y0, z0).setColor(r, g, b, alpha).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, alpha).setUv(0, vSize)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, alpha).setUv(uSize, vSize)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, x3, y3, z3).setColor(r, g, b, alpha).setUv(uSize, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
    }
}
