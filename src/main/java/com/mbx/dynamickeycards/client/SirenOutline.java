package com.mbx.dynamickeycards.client;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.block.SirenBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Redraws the siren's selection outline after the translucent terrain pass instead of before it.
 *
 * <p>Vanilla flushes the block outline while rendering solid geometry, well before
 * {@code RenderType.translucent}. That is invisible for an ordinary block, and even for vanilla
 * stained glass, whose outline sits on a full cube's silhouette where its own faces are edge-on
 * and cover nothing. The siren's lens is an inset cuboid, so its outline edges land squarely
 * behind glass that has not been drawn yet - the glass then paints over the black lines and the
 * outline reads as absent. Lowering the glass alpha doesn't fix it; the lines are covered, not
 * merely tinted.
 *
 * <p>So: cancel vanilla's pass for this block and draw the identical shape, in vanilla's own
 * colour and line type, at {@link RenderLevelStageEvent.Stage#AFTER_PARTICLES} - which is past the
 * translucent pass, the same stage this mod's other world-space boxes already use.
 *
 * <p>Whether an outline belongs on screen at all is vanilla's call, not this class's: hidden GUI,
 * spectator, adventure mode without the right tool, or simply not looking at a block. Rather than
 * restate any of that, the cancelled event <em>is</em> the answer - it only fires when vanilla was
 * about to draw - so {@link #onRenderHighlight} hands the position to the stage below and the
 * stage draws nothing on a frame it wasn't given one.
 */
@EventBusSubscriber(modid = DynamicKeycards.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class SirenOutline {

    /** Vanilla's own outline colour, from {@code LevelRenderer#renderHitOutline}. */
    private static final float ALPHA = 0.4f;

    /** Set for the frame vanilla asked for a siren outline, consumed by the stage below. */
    @Nullable
    private static BlockPos pending;

    private SirenOutline() {
    }

    @SubscribeEvent
    static void onRenderHighlight(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos pos = event.getTarget().getBlockPos();
        if (minecraft.level != null && minecraft.level.getBlockState(pos).getBlock() instanceof SirenBlock) {
            event.setCanceled(true);
            pending = pos;
        }
    }

    @SubscribeEvent
    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        BlockPos pos = pending;
        pending = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (pos == null || minecraft.level == null) {
            return;
        }
        BlockState state = minecraft.level.getBlockState(pos);
        if (!(state.getBlock() instanceof SirenBlock)) {
            return;
        }
        Entity camera = minecraft.getCameraEntity();
        VoxelShape shape = state.getShape(minecraft.level, pos,
                camera == null ? CollisionContext.empty() : CollisionContext.of(camera));
        if (shape.isEmpty()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        renderShape(event.getPoseStack().last(), lines, shape,
                pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
        buffer.endBatch(RenderType.lines());
    }

    /**
     * {@code LevelRenderer#renderShape}, which is private - one line segment per shape edge, each
     * carrying its own direction as the normal, exactly as vanilla buffers them.
     */
    private static void renderShape(PoseStack.Pose pose, VertexConsumer consumer, VoxelShape shape,
                                    double x, double y, double z) {
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float dx = (float) (x2 - x1);
            float dy = (float) (y2 - y1);
            float dz = (float) (z2 - z1);
            float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= length;
            dy /= length;
            dz /= length;
            consumer.addVertex(pose, (float) (x1 + x), (float) (y1 + y), (float) (z1 + z))
                    .setColor(0f, 0f, 0f, ALPHA).setNormal(pose, dx, dy, dz);
            consumer.addVertex(pose, (float) (x2 + x), (float) (y2 + y), (float) (z2 + z))
                    .setColor(0f, 0f, 0f, ALPHA).setNormal(pose, dx, dy, dz);
        });
    }
}
