package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.client.BoxRenderUtil;
import com.mbx.dynamickeycards.client.Eased;
import com.mbx.dynamickeycards.network.SensorRangeAdjustPayload;
import com.mbx.dynamickeycards.network.SensorRangeCommitPayload;
import com.mbx.dynamickeycards.registry.DKComponents;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/**
 * Client side of range-editing (see {@code MotionSensorBlock#tryRangeEditInteraction}): the
 * Ctrl+Scroll input that adjusts a sensor's edit box, the right/left-click input that confirms
 * or cancels it, and the highlight box (edges + striped faces) that previews it. The box itself
 * and whether editing is even active both live server-side on the {@link MotionSensorBlockEntity}
 * and reach the client only through ordinary block-entity sync - this class never invents state
 * of its own, just tracks which armed sensor to react to and renders whatever the synced
 * {@link MotionSensorBlockEntity#getEditZone()} says.
 *
 * <p>Three separate questions, each answered differently:
 * <ul>
 *   <li><b>Tracked for input</b> ({@link #activeTarget}): whichever hand holds a redstone dust
 *   stack carrying {@link DKComponents#RANGE_EDIT_TARGET} (stamped by
 *   {@code MotionSensorBlock#tryRangeEditInteraction} when it's armed, cleared again on
 *   confirm/cancel), full stop - not the crosshair, not a "last looked at" memory. Scrolling/
 *   clicking only ever acts on this. The instant it goes {@code null} (the tagged dust leaves
 *   both hands), input stops immediately - no lingering.</li>
 *   <li><b>Rendered at all</b> ({@link #highlightTarget}): follows {@link #activeTarget}, but
 *   lags behind it on the way out - losing the tagged dust doesn't cut the highlight instantly,
 *   it fades over {@link #FADE_TICKS} (same borrowed formula as {@code DKClientEvents}' own
 *   held-item highlight), so switching hands or briefly swapping items doesn't read as an abrupt
 *   flicker.</li>
 *   <li><b>Hovered</b> (the look ray actually hits the box right now): full line width plus the
 *   striped face texture; not hovered: a permanently thin outline with no face texture at all -
 *   not a fade-to-nothing on its own, just a smaller stable width, eased between the two (see
 *   {@link #widthEased}) rather than snapped. This rides on top of the fade above, not
 *   instead of it: both apply at once.</li>
 * </ul>
 */
@EventBusSubscriber(modid = DynamicKeycards.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class SensorRangeClientHandler {

    /** Generous on purpose - "held the tagged dust" is the real gate, this is just sanity so it doesn't render across the whole map. */
    private static final double MAX_RANGE = 48.0;
    /** A muted red - toned down from an earlier, much more saturated version. */
    private static final int EDGE_COLOR = 0xCC5C54;
    /** Hovered - the look ray actually hits the box. */
    private static final float LINE_WIDTH_HOVERED = 1 / 20f;
    /** Armed but not currently hovered - thin and stable, not a fade step. */
    private static final float LINE_WIDTH_AMBIENT = LINE_WIDTH_HOVERED / 4f;
    /** The face the player's currently facing - see {@link RangeBox#hitFace}. */
    private static final float FACE_ALPHA_LOOKED = 0.6f;
    /** Every other face - a faint hint that the box extends there too. */
    private static final float FACE_ALPHA_OTHER = 0.18f;
    /** How long a grow/shrink visually eases into place, rather than snapping instantly. */
    private static final long ANIM_DURATION_NANOS = 140_000_000L;
    /** How long the line eases between {@link #LINE_WIDTH_AMBIENT} and {@link #LINE_WIDTH_HOVERED} when hover starts/stops. */
    private static final long WIDTH_ANIM_DURATION_NANOS = 120_000_000L;
    /** How long {@link #highlightTarget} lingers after {@link #activeTarget} goes null, fading out - same cadence {@code DKClientEvents} uses. */
    private static final int FADE_TICKS = 8;
    /** How many ticks of {@link #activeTarget} being null before the fade actually starts counting down - see {@code DKClientEvents}' own {@code TTL}. */
    private static final int TTL = 2;
    /** Below this alpha, stop rendering entirely - see {@code DKClientEvents} for the fade curve this pairs with. */
    private static final float ALPHA_CUTOFF = 1 / 8f;

    /**
     * Diagonal stripes, not a flat fill. Has no accompanying {@code .png.mcmeta}, so it loads
     * with {@code clamp=false} (vanilla's default - see {@code SimpleTexture#load}), i.e. real
     * GL_REPEAT wrapping - {@link #bufferFace} relies on that to tile continuously (including a
     * fractional partial tile at the edge) across whatever size the face is *right now*, which is
     * what lets the stripes visibly grow/shrink smoothly along with the animated box instead of
     * only snapping once the animation settles.
     */
    private static final ResourceLocation STRIPE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/misc/range_stripe.png");

    private static final RenderType EDGE_LIT =
            BoxRenderUtil.opaqueLitBoxType("dynamickeycards:sensor_range_edges", BoxRenderUtil.WHITE_TEXTURE);

    /** Same as {@link #EDGE_LIT} but translucent, unculled, and textured with {@link #STRIPE_TEXTURE}. */
    private static final RenderType FACE_TRANSLUCENT = RenderType.create(
            "dynamickeycards:sensor_range_face",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(STRIPE_TEXTURE, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false)
    );

    @Nullable
    private static BlockPos activeTarget;
    /** What actually gets rendered - lags behind {@link #activeTarget} on the way out, see the class doc. */
    @Nullable
    private static BlockPos highlightTarget;
    private static int ticksTillRemoval = -FADE_TICKS - 1;

    /**
     * Fractional scroll carried over between events, consumed one whole cell at a time - see
     * {@link #onMouseScroll}. Reset whenever {@link #activeTarget} changes (including to
     * {@code null}) so leftover fraction from one sensor's session never bleeds into the next.
     */
    private static double scrollAccumulator;

    /** The position {@link #boxEased} is currently easing for - a position change snaps instead of easing, see {@link #animatedBox}. */
    @Nullable
    private static BlockPos boxEasedPos;
    private static final Eased<AABB> boxEased = new Eased<>(ANIM_DURATION_NANOS, SensorRangeClientHandler::lerpAABB, new AABB(BlockPos.ZERO));
    private static final Eased<Float> widthEased = new Eased<>(WIDTH_ANIM_DURATION_NANOS, SensorRangeClientHandler::lerpFloat, LINE_WIDTH_AMBIENT);

    /**
     * The last real {@code editZone} seen for {@link #lastKnownZonePos}, and that position - kept
     * around purely so {@link #onRenderLevelStage} still has box data to draw during a
     * confirm/cancel fade-out, after the sensor's own {@code editZone} has already gone back to
     * {@code null}. Only ever read back for the *same* position it was captured at - see that
     * method for why a stale zone from a different, earlier sensor must never leak through here.
     */
    @Nullable
    private static RangeBox lastKnownZone;
    @Nullable
    private static BlockPos lastKnownZonePos;

    /**
     * Refreshes {@link #activeTarget} once a tick - {@link #onMouseScroll}/{@link #onClickInput}
     * deliberately read the field this writes rather than resolving their own target inline: a
     * GLFW input callback can land at any point relative to the render loop, so a fresh
     * resolution wouldn't reliably reflect the same held-item state {@link #onRenderLevelStage}
     * saw this frame.
     */
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        Level level = Minecraft.getInstance().level;
        BlockPos previousActiveTarget = activeTarget;
        activeTarget = (player != null && level != null) ? resolveLiveTarget(player, level) : null;
        if (!Objects.equals(activeTarget, previousActiveTarget)) {
            scrollAccumulator = 0;
        }
        if (activeTarget != null) {
            highlightTarget = activeTarget;
            ticksTillRemoval = TTL;
        } else {
            ticksTillRemoval--;
            if (ticksTillRemoval < -FADE_TICKS) {
                highlightTarget = null;
            }
        }
    }

    /**
     * {@code priority = HIGHEST, receiveCanceled = true}: NeoForge's event bus skips
     * already-cancelled events for any listener that doesn't opt into {@code receiveCanceled} -
     * Create's own ecosystem has plenty of other scroll-consuming interactions (filters, gauges,
     * zoom), and running at the default priority meant any of them cancelling the event first
     * would silently swallow ours before it ever ran, with no error - indistinguishable from
     * "nothing happens" and exactly as inconsistent as which other mod's listener happened to
     * see the scroll first.
     *
     * <p>The hotbar lock itself (cancelling the event) doesn't require hovering - only holding
     * the tagged dust with Ctrl down. Gating the lock on hover too would mean a moment of the
     * cursor drifting off the box mid-adjustment lets one scroll notch slip through to the
     * hotbar before the next one resumes adjusting; locking it for the whole time Ctrl is held
     * means losing and regaining hover doesn't cost anything. Only the *adjustment itself* still
     * requires hovering.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        // activeTarget being non-null already means a hand holds the tagged dust for it - Ctrl is
        // still checked explicitly since it collides with vanilla's own default Sprint keybind,
        // so without it a player merely sprinting near an armed sensor while scrolling their
        // hotbar would have every one of those scrolls silently hijacked into an adjustment
        if (!isControlDown() || activeTarget == null || player == null) {
            return;
        }
        event.setCanceled(true);
        double delta = event.getScrollDeltaY();
        if (delta == 0 || !isHovering(Minecraft.getInstance().level, player, activeTarget)) {
            return;
        }
        // A physical mouse wheel notch is one whole unit of delta; a trackpad instead sends a
        // rapid stream of many much smaller fractional ones for what's meant to be a single
        // gesture. Reacting to every nonzero delta as its own full cell of growth meant a light
        // trackpad swipe could blow the box out to its envelope limit almost instantly.
        //
        // Accumulating and consuming a WHOLE unit at a time fixes that, but only sending exactly
        // one adjust packet per event even when the accumulated total has crossed more than one
        // whole unit (a heavier swipe, or an OS scroll-speed multiplier scaling a single physical
        // notch past 1.0) matters just as much: sending every crossed unit's packet in the same
        // event let two (or more) land within the same render frame, and since the box is only
        // ever sampled once per frame, that skipped the in-between size entirely - visually a
        // sudden 2-cell jump instead of two separate 1-cell steps. Consuming only one unit per
        // event, and leaving any remainder in the accumulator for the next event to consume,
        // keeps every visible step exactly one cell, however many events it took to get there.
        scrollAccumulator += delta;
        if (scrollAccumulator <= -1.0) {
            scrollAccumulator += 1.0;
            PacketDistributor.sendToServer(new SensorRangeAdjustPayload(activeTarget, true)); // grow
        } else if (scrollAccumulator >= 1.0) {
            scrollAccumulator -= 1.0;
            PacketDistributor.sendToServer(new SensorRangeAdjustPayload(activeTarget, false)); // shrink
        }
    }

    /**
     * Right-click confirms, left-click cancels - only while the cursor is actually hovering the
     * highlight (a real ray hit test against the current box, same as everything else here), so
     * a right-click elsewhere still does whatever that block/item would otherwise do. Left-click
     * is different: it's always suppressed while holding the tagged dust, hovering or not - that
     * dust is spoken for, so it shouldn't be able to break blocks with a stray left-click either,
     * the same reasoning {@link #onMouseScroll}'s hotbar lock uses. (A bare-hand click on the
     * physical block itself still separately cancels too, see {@code MotionSensorBlock#tryCancelRangeEdit} -
     * unrelated to this, since holding nothing means {@link #activeTarget} is already {@code null}
     * here.) Same {@code HIGHEST}/{@code receiveCanceled} posture as {@link #onMouseScroll} and
     * for the same reason.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    static void onClickInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() && !event.isUseItem()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        BlockPos pos = activeTarget;
        if (pos == null || player == null) {
            return;
        }
        boolean hovering = isHovering(minecraft.level, player, pos);
        if (event.isAttack()) {
            event.setCanceled(true);
            if (hovering) {
                PacketDistributor.sendToServer(new SensorRangeCommitPayload(pos, false));
            }
            return;
        }
        if (hovering) {
            PacketDistributor.sendToServer(new SensorRangeCommitPayload(pos, true));
            event.setCanceled(true);
        }
    }

    /**
     * The literal physical Control key, on every platform - unlike {@code Screen#hasControlDown()},
     * which on macOS checks Cmd instead (vanilla remaps "Control" to Cmd there for clipboard-style
     * shortcut consistency with the rest of the OS). That remap makes sense for Ctrl+C/V/X, but
     * this feature was asked for as literally "hold Control", so it should mean Control everywhere
     * rather than silently requiring Cmd on a Mac.
     */
    private static boolean isControlDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    /** Whether {@code player}'s look ray actually hits {@code pos}'s current edit box right now. */
    private static boolean isHovering(@Nullable Level level, LocalPlayer player, BlockPos pos) {
        return level != null && level.getBlockEntity(pos) instanceof MotionSensorBlockEntity sensor
                && sensor.getEditZone() instanceof RangeBox editZone
                && editZone.hitFace(pos, player.getEyePosition(1f), player.getLookAngle()) != null;
    }

    @SubscribeEvent
    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float fadeAlpha = currentAlpha(partialTick);
        if (fadeAlpha < ALPHA_CUTOFF) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos pos = highlightTarget;
        LocalPlayer player = minecraft.player;
        if (pos == null || player == null || minecraft.level == null) {
            return;
        }
        // confirming/cancelling clears the sensor's own editZone the same instant it happens -
        // otherwise there'd be nothing left to render for the rest of the fade-out (ticksTillRemoval
        // still counts down fine on its own, but with no box data there's nothing to draw), so the
        // last real zone seen for this exact position is cached and falls back in for exactly that
        // window; a *different* position's stale zone is never reused, see #lastKnownZone's own doc
        RangeBox editZone;
        if (minecraft.level.getBlockEntity(pos) instanceof MotionSensorBlockEntity sensor && sensor.getEditZone() instanceof RangeBox live) {
            editZone = live;
            lastKnownZonePos = pos;
            lastKnownZone = live;
        } else if (pos.equals(lastKnownZonePos) && lastKnownZone != null) {
            editZone = lastKnownZone;
        } else {
            return;
        }
        AABB logicalBox = editZone.toAABB(pos);
        AABB animatedBox = animatedBox(pos, logicalBox);
        Direction lookedFace = editZone.hitFace(pos, player.getEyePosition(partialTick), player.getLookAngle());
        boolean hovered = lookedFace != null;
        // EDGE_LIT's opaque shader doesn't actually blend on the vertex alpha under vanilla
        // rendering (same as DKClientEvents' own edge renderer - see its doc for why: alpha only
        // does something there under a shader pack's replacement fragment shader), so the fade
        // has to ride on the width shrinking toward zero instead, same as that class does
        float lineWidth = widthEased.towards(hovered ? LINE_WIDTH_HOVERED : LINE_WIDTH_AMBIENT) * fadeAlpha;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose pose = poseStack.last();

        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        float r = ((EDGE_COLOR >> 16) & 0xFF) / 255f;
        float g = ((EDGE_COLOR >> 8) & 0xFF) / 255f;
        float b = (EDGE_COLOR & 0xFF) / 255f;

        VertexConsumer edgeConsumer = buffer.getBuffer(EDGE_LIT);
        BoxRenderUtil.renderThickBoxEdges(pose, edgeConsumer, animatedBox, lineWidth, r, g, b, fadeAlpha);
        buffer.endBatch(EDGE_LIT);

        // ambient (not hovered): no face texture at all
        if (hovered) {
            VertexConsumer faceConsumer = buffer.getBuffer(FACE_TRANSLUCENT);
            for (Direction face : Direction.values()) {
                float alpha = (face == lookedFace ? FACE_ALPHA_LOOKED : FACE_ALPHA_OTHER) * fadeAlpha;
                bufferFace(pose, faceConsumer, animatedBox, face, r, g, b, alpha);
            }
            buffer.endBatch(FACE_TRANSLUCENT);
        }

        poseStack.popPose();
    }

    /** {@code alpha = lerp(prevTick, curTick)³} - same borrowed curve {@code DKClientEvents} uses. */
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

    /**
     * Eases {@link #boxEased} toward {@code logicalBox} - except on a {@code pos} change, which
     * snaps instead: {@link Eased} interpolates in world-space, tied to wherever the *previous*
     * sensor physically was, so easing it toward a different sensor's box would visibly slide the
     * highlight across the world between two unrelated positions rather than resizing in place.
     */
    private static AABB animatedBox(BlockPos pos, AABB logicalBox) {
        if (!pos.equals(boxEasedPos)) {
            boxEased.snapTo(logicalBox);
            boxEasedPos = pos;
        }
        return boxEased.towards(logicalBox);
    }

    private static AABB lerpAABB(AABB from, AABB to, float t) {
        return new AABB(
                Mth.lerp(t, from.minX, to.minX), Mth.lerp(t, from.minY, to.minY), Mth.lerp(t, from.minZ, to.minZ),
                Mth.lerp(t, from.maxX, to.maxX), Mth.lerp(t, from.maxY, to.maxY), Mth.lerp(t, from.maxZ, to.maxZ));
    }

    /**
     * {@link Eased.Lerp}'s parameter order is {@code (from, to, t)}; {@code Mth.lerp}'s is
     * {@code (pct, start, end)} - same three values, different order, so a direct
     * {@code Mth::lerp} method reference silently binds them positionally wrong instead of
     * failing to compile. This just calls it correctly.
     */
    private static float lerpFloat(float from, float to, float t) {
        return Mth.lerp(t, from, to);
    }

    /**
     * The sensor being tracked for rendering/input right now, if any - driven entirely by
     * {@link DKComponents#RANGE_EDIT_TARGET} on whatever's in {@code player}'s hands, not the
     * crosshair - see the class doc for why.
     */
    @Nullable
    private static BlockPos resolveLiveTarget(LocalPlayer player, Level level) {
        BlockPos target = heldRangeEditTarget(player.getMainHandItem());
        if (target == null) {
            target = heldRangeEditTarget(player.getOffhandItem());
        }
        return target != null && isArmedSensor(level, target) && withinRange(player, target) ? target : null;
    }

    @Nullable
    private static BlockPos heldRangeEditTarget(ItemStack stack) {
        return stack.is(Items.REDSTONE) ? stack.get(DKComponents.RANGE_EDIT_TARGET.get()) : null;
    }

    private static boolean withinRange(LocalPlayer player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MAX_RANGE * MAX_RANGE;
    }

    private static boolean isArmedSensor(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof MotionSensorBlock
                && level.getBlockEntity(pos) instanceof MotionSensorBlockEntity sensor
                && sensor.isRangeEditMode();
    }

    /**
     * {@code box}'s face facing {@code face}, as one quad spanning the whole face - not one quad
     * per unit cell. The UV extent is set to the face's actual current width/height in blocks
     * (not normalized 0..1), and {@link #STRIPE_TEXTURE} tiles across that via real GL_REPEAT
     * wrapping (see its own doc) - which is also what makes it follow {@code box} smoothly when
     * called with the animated box mid-resize: the tile count is just however many whole (and
     * one partial) repeats fit in the current fractional size, exactly like a continuously
     * stretching physical material rather than a texture that has to wait for the size to settle
     * on a whole number before it looks right. Only the fixed (perpendicular-to-the-face)
     * coordinate is inset, to avoid z-fighting with the edge lines.
     */
    private static void bufferFace(PoseStack.Pose pose, VertexConsumer consumer, AABB box, Direction face,
                                    float r, float g, float b, float alpha) {
        double inset = 0.01;
        float minX = (float) box.minX, maxX = (float) box.maxX;
        float minY = (float) box.minY, maxY = (float) box.maxY;
        float minZ = (float) box.minZ, maxZ = (float) box.maxZ;
        float xSize = maxX - minX, ySize = maxY - minY, zSize = maxZ - minZ;
        switch (face) {
            case DOWN -> {
                float y = (float) (box.minY + inset);
                BoxRenderUtil.litQuad(pose, consumer, minX, y, maxZ, minX, y, minZ, maxX, y, minZ, maxX, y, maxZ, xSize, zSize, r, g, b, alpha);
            }
            case UP -> {
                float y = (float) (box.maxY - inset);
                BoxRenderUtil.litQuad(pose, consumer, minX, y, minZ, minX, y, maxZ, maxX, y, maxZ, maxX, y, minZ, xSize, zSize, r, g, b, alpha);
            }
            case NORTH -> {
                float z = (float) (box.minZ + inset);
                BoxRenderUtil.litQuad(pose, consumer, maxX, maxY, z, maxX, minY, z, minX, minY, z, minX, maxY, z, xSize, ySize, r, g, b, alpha);
            }
            case SOUTH -> {
                float z = (float) (box.maxZ - inset);
                BoxRenderUtil.litQuad(pose, consumer, minX, maxY, z, minX, minY, z, maxX, minY, z, maxX, maxY, z, xSize, ySize, r, g, b, alpha);
            }
            case WEST -> {
                float x = (float) (box.minX + inset);
                BoxRenderUtil.litQuad(pose, consumer, x, maxY, minZ, x, minY, minZ, x, minY, maxZ, x, maxY, maxZ, zSize, ySize, r, g, b, alpha);
            }
            case EAST -> {
                float x = (float) (box.maxX - inset);
                BoxRenderUtil.litQuad(pose, consumer, x, maxY, maxZ, x, minY, maxZ, x, minY, minZ, x, maxY, minZ, zSize, ySize, r, g, b, alpha);
            }
        }
    }
}
