package com.mbx.dynamickeycards.client;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.block.SirenBlock;
import com.mbx.dynamickeycards.block.SirenBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Draws the siren's turning innards - the reflector, its socket, and the bulb - on top of the
 * static shade/lens/tip the block model already provides.
 *
 * <p>They live here rather than in the block model because the reflector turns: the beam sweeps a
 * full circle in {@value SirenBlockEntity#FRAMES_PER_TURN} steps, and driving that through block
 * states would mean a block update every few ticks per siren. The frame comes from the world clock
 * (see {@link SirenBlockEntity#animationFrame}), so nothing is sent to the client at all.
 *
 * <p><b>The 45 degrees between odd and even steps is built into the models, not applied as a
 * transform.</b> Two shapes cover the eight steps - A opens along an axis and serves the even
 * steps, B opens on the diagonal and serves the odd ones - so the transform advances a quarter
 * turn every <em>two</em> steps and the pair of models supplies the halves in between. Turning the
 * odd steps a further 45 degrees would apply that offset twice.
 *
 * <p>The whole assembly turns together, base included; the base and the bulb only look fixed
 * because both are symmetric under a quarter turn.
 */
public class SirenRenderer implements BlockEntityRenderer<SirenBlockEntity> {

    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "block/siren/" + path));
    }

    /**
     * The core, in four bakes. Two shapes (axis-aligned / diagonal) times two quarter-turn parities:
     * the block entity render path applies no directional face shading of its own (see
     * {@code ModelBlockRenderer#renderQuadList}, which hands untinted quads a flat 1.0), so the
     * chunk renderer's own factors are baked into the textures - and a face that has turned a
     * quarter needs the factor of the world direction it now points at, not the one it started on.
     */
    private static final ModelResourceLocation[] CORE = {
            model("siren_core_a"), model("siren_core_b"),
            model("siren_core_a_q"), model("siren_core_b_q")};
    /**
     * The same four bakes with the bulb's own light baked in on top. The prototype lights the
     * reflector with a real point light inside the lens ({@code PointLight('#ffe6b0', .32, .6, 2)}),
     * which a block model has no equivalent for - so each face's share of it is worked out once
     * from the face's distance and normal and folded into the texture, as a second set of columns
     * the lit models' UVs point at. The light rides the rotor in the prototype too, so the bright
     * patch is fixed relative to the reflector and one lit twin per bake covers all eight frames.
     */
    private static final ModelResourceLocation[] CORE_LIT = {
            model("siren_core_a_on"), model("siren_core_b_on"),
            model("siren_core_a_q_on"), model("siren_core_b_q_on")};
    private static final ModelResourceLocation[] BULB_OFF = {model("siren_bulb"), model("siren_bulb_q")};
    private static final ModelResourceLocation[] BULB_ON = {model("siren_bulb_on"), model("siren_bulb_on_q")};
    /**
     * The lens and tip while lit, split in two so the beam can be brighter than the rest: the part
     * the beam points at is drawn at full brightness, everything else at the light the world gives
     * it. One draw cannot do both, and the two halves tile the same faces rather than overlapping,
     * so there is nothing stacked or offset. Which part is which changes every frame, hence a pair
     * per shape. The unlit block model carries its own copy of this geometry - exactly one of the
     * two is ever drawn.
     */
    private static final ModelResourceLocation[] GLASS_BEAM = {model("siren_glass_a_beam"), model("siren_glass_b_beam")};
    private static final ModelResourceLocation[] GLASS_DIM = {model("siren_glass_a_dim"), model("siren_glass_b_dim")};

    /** A quarter turn every two steps - see the class doc for where the other 45 degrees comes from. */
    private static final float DEGREES_PER_STEP_PAIR = 90f;

    /**
     * What one animation frame means to the models. Three separate things fall out of it, which is
     * why they are named here rather than worked out again at each use: which of the two
     * {@link #shape}s the frame is drawn from, which quarter-turn {@link #quarter} its baked
     * shading belongs to, and how far the assembly has {@link #degrees turned}. They come apart
     * because a turn advances a quarter every <em>two</em> frames - see the class doc.
     */
    private record Sweep(int shape, int quarter, float degrees) {

        static Sweep of(int frame) {
            int quarterTurns = frame / 2;
            return new Sweep(frame % 2, quarterTurns % 2, quarterTurns * DEGREES_PER_STEP_PAIR);
        }

        /** Index into {@link #CORE} and {@link #CORE_LIT}, which hold both shapes at both parities. */
        int core() {
            return quarter * 2 + shape;
        }
    }

    /** Everything {@code DKClientSetup} must have baked - nothing here is reachable from a blockstate. */
    public static List<ModelResourceLocation> models() {
        return Stream.of(CORE, CORE_LIT, BULB_OFF, BULB_ON, GLASS_BEAM, GLASS_DIM)
                .flatMap(Arrays::stream)
                .toList();
    }

    private final BlockRenderDispatcher blocks;

    public SirenRenderer(BlockEntityRendererProvider.Context context) {
        this.blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(SirenBlockEntity siren, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        Level level = siren.getLevel();
        if (level == null) {
            return;
        }
        BlockState state = siren.getBlockState();
        if (!(state.getBlock() instanceof SirenBlock)) {
            return;
        }
        Sweep sweep = Sweep.of(siren.animationFrame(level.getGameTime(), partialTick));
        boolean lit = siren.isSpinning();

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        // the block state's own rotation - taking MC's for the exact variant keeps this in step
        // with siren.json rather than re-deriving it here
        poseStack.mulPose(mountRotation(state).getRotation().getMatrix());
        poseStack.mulPose(Axis.YP.rotationDegrees(sweep.degrees()));
        poseStack.translate(-0.5, -0.5, -0.5);

        renderModel((lit ? CORE_LIT : CORE)[sweep.core()], state, poseStack, buffers,
                light, overlay, RenderType.solid());
        // the bulb is its own model so it can be drawn at full brightness while the metal around
        // it stays lit by the world
        renderModel((lit ? BULB_ON : BULB_OFF)[sweep.quarter()], state, poseStack, buffers,
                lit ? LightTexture.FULL_BRIGHT : light, overlay, RenderType.solid());
        if (lit) {
            // the glass turns with the beam, so it shares this same transform - which is the whole
            // reason it is drawn inside this block rather than under a second copy of it
            renderModel(GLASS_DIM[sweep.shape()], state, poseStack, buffers,
                    light, overlay, Sheets.translucentCullBlockSheet());
            renderModel(GLASS_BEAM[sweep.shape()], state, poseStack, buffers,
                    LightTexture.FULL_BRIGHT, overlay, Sheets.translucentCullBlockSheet());
        }
        poseStack.popPose();
    }

    private void renderModel(ModelResourceLocation location, BlockState state, PoseStack poseStack,
                             MultiBufferSource buffers, int light, int overlay, RenderType type) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(location);
        blocks.getModelRenderer().renderModel(poseStack.last(), buffers.getBuffer(type),
                state, model, 1f, 1f, 1f, light, overlay);
    }

    /** The rotation {@code blockstates/siren.json} applies for this variant, taken from MC's own table. */
    private static BlockModelRotation mountRotation(BlockState state) {
        Direction facing = state.getValue(SirenBlock.FACING);
        AttachFace face = state.getValue(SirenBlock.FACE);
        return switch (face) {
            case FLOOR -> BlockModelRotation.by(0, flatYaw(facing));
            case CEILING -> BlockModelRotation.by(180, flatYaw(facing));
            case WALL -> BlockModelRotation.by(270, wallYaw(facing));
        };
    }

    private static int flatYaw(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    private static int wallYaw(Direction facing) {
        return switch (facing) {
            case EAST -> 270;
            case WEST -> 90;
            case SOUTH -> 0;
            default -> 180;
        };
    }

    /**
     * Vanilla's beacon distance, four times the default. Everything that makes a lit siren look
     * like one is drawn here - the turning core, and the lens once the block model drops it - so
     * past this a working siren is a bare metal plate. A warning light has to read from further
     * off than the 64 blocks a chest gets.
     */
    @Override
    public int getViewDistance() {
        return 256;
    }
}
