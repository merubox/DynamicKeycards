package com.mbx.dynamickeycards.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A sensor's detection zone as six inclusive block-cell offsets from its own position, relative
 * to the fixed envelope {@link #envelopeFor} computes for its {@link MotionSensorBlock#openDirection}.
 * Any axis-aligned sub-box within that envelope is a legal zone - the selection doesn't have to
 * include the sensor's own cell (offset (0,0,0)) at all, it just can't be pulled inside-out
 * (every axis keeps {@code min <= max}) or pushed past the envelope's own bounds.
 *
 * <p>The envelope itself has three axes, each with its own shape:
 * <ul>
 *   <li>The axis running along {@code openDirection} only ever reaches 0..2 cells that way - a
 *   sensor can never reach back through its own mounting surface.</li>
 *   <li>The vertical (Y) axis, when it isn't already the one above (a wall sensor's is
 *   horizontal, so this applies to it; a ceiling sensor's {@code openDirection} already *is* Y,
 *   so this doesn't add anything further for it), only ever reaches 0..-2 - downward only, same
 *   idea as a real proximity sensor's typical detection cone, never upward through the wall it's
 *   mounted flush against on that axis either.</li>
 *   <li>Whatever's left over (a wall sensor's one remaining horizontal axis; nothing further for
 *   a ceiling sensor, whose other two axes are both already covered by the first bullet) extends
 *   -1..1, centered on the sensor.</li>
 * </ul>
 *
 * <p>This shape is identical for every {@link MotionSensorBlock} regardless of which way it's
 * actually rotated - a wall sensor's envelope depends on its horizontal {@code FACING}, a
 * ceiling sensor's is fixed (its {@code openDirection} is always {@code DOWN}).
 */
public record RangeBox(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {

    /**
     * The zone every sensor used before this range-editing feature existed: its own cell plus
     * the one directly below, regardless of which way it's mounted. Still what a sensor that's
     * never had its range edited uses - see {@code MotionSensorBlockEntity#detectionZone}.
     */
    public static final RangeBox LEGACY_DEFAULT = new RangeBox(0, 0, -1, 0, 0, 0);

    /** A box covering only the sensor's own cell - the starting point for a fresh edit. */
    public static final RangeBox SINGLE_CELL = new RangeBox(0, 0, 0, 0, 0, 0);

    public AABB toAABB(BlockPos pos) {
        return new AABB(
                pos.getX() + minX, pos.getY() + minY, pos.getZ() + minZ,
                pos.getX() + maxX + 1.0, pos.getY() + maxY + 1.0, pos.getZ() + maxZ + 1.0);
    }

    /** See the class doc for the shape this describes. */
    public static RangeBox envelopeFor(Direction openDirection) {
        int[] near = {0, 0, 0};
        int[] far = {0, 0, 0};
        int yAxis = Direction.Axis.Y.ordinal();
        for (Direction.Axis axis : Direction.Axis.values()) {
            int i = axis.ordinal();
            if (openDirection.getAxis() == axis) {
                if (openDirection.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                    far[i] = 2;
                } else {
                    near[i] = -2;
                }
            } else if (i == yAxis) {
                // downward only, even for a wall sensor whose open axis is horizontal - see class doc
                near[i] = -2;
            } else {
                near[i] = -1;
                far[i] = 1;
            }
        }
        return new RangeBox(near[0], far[0], near[1], far[1], near[2], far[2]);
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * Extends the bound facing {@code direction} outward by one cell, clamped to
     * {@link #envelopeFor}({@code openDirection}) - a no-op once that bound is already at its
     * envelope limit. Every one of the 6 bounds can move this way, including the one facing
     * {@code openDirection.getOpposite()} (flush against the mounting surface by default) - it's
     * free to pull away from the surface just like any other bound, it just can't cross past the
     * envelope's own limit there (i.e. through the surface) - see the class doc for why a
     * selection isn't required to keep including the sensor's own cell.
     */
    public RangeBox grow(Direction direction, Direction openDirection) {
        return shift(direction, openDirection, 1);
    }

    /** {@link #grow}, but pulling the bound facing {@code direction} inward by one cell instead. */
    public RangeBox shrink(Direction direction, Direction openDirection) {
        return shift(direction, openDirection, -1);
    }

    /**
     * Moves the bound facing {@code direction} by {@code sign} cells, dragging the *other* bound
     * on that axis along with it if that would otherwise cross past it - shrinking a
     * already-1-cell-wide axis from either side moves the whole thing over by one instead of
     * refusing outright, and does so the same way regardless of which of the two bounds is the
     * one actually being moved. That symmetry needs deliberate handling: clamping the two bounds
     * independently (min first, then max no lower than the new min, in whichever order they
     * happen to be computed) only ever drags the *second*-computed one - shrinking from the
     * "min" side would drag max along fine, but shrinking from the "max" side would just snap
     * straight back to a no-op instead of dragging min, since min was already fixed by the time
     * max's turn came.
     */
    private RangeBox shift(Direction direction, Direction openDirection, int sign) {
        RangeBox envelope = envelopeFor(openDirection);
        boolean positive = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        int delta = positive ? sign : -sign;
        return switch (direction.getAxis()) {
            case X -> {
                int[] b = moveBound(minX, maxX, positive, delta, envelope.minX, envelope.maxX);
                yield new RangeBox(b[0], b[1], minY, maxY, minZ, maxZ);
            }
            case Y -> {
                int[] b = moveBound(minY, maxY, positive, delta, envelope.minY, envelope.maxY);
                yield new RangeBox(minX, maxX, b[0], b[1], minZ, maxZ);
            }
            case Z -> {
                int[] b = moveBound(minZ, maxZ, positive, delta, envelope.minZ, envelope.maxZ);
                yield new RangeBox(minX, maxX, minY, maxY, b[0], b[1]);
            }
        };
    }

    /** {@code {newMin, newMax}} - see {@link #shift} for why the drag has to be handled explicitly like this. */
    private static int[] moveBound(int min, int max, boolean movingMax, int delta, int envMin, int envMax) {
        if (movingMax) {
            int newMax = clampInt(max + delta, envMin, envMax);
            return new int[]{Math.min(min, newMax), newMax};
        }
        int newMin = clampInt(min + delta, envMin, envMax);
        return new int[]{newMin, Math.max(max, newMin)};
    }

    /**
     * The face of {@code toAABB(pos)} a ray from {@code origin} toward {@code direction} actually
     * enters through - shared by {@code DKNetwork} (server-side, to resolve what a Ctrl+Scroll
     * notch should grow/shrink) and {@code SensorRangeClientHandler} (client-side, to know which
     * face to render brighter). Deliberately a real ray-box intersection rather than just
     * {@link Direction#getNearest}(direction) - looking *at* a box's near face means aiming
     * *through* it, i.e. the ray's direction vector roughly opposes that face's own outward
     * normal, so naively rounding the raw look vector to the nearest axis resolves to the *far*
     * face instead of the one actually being looked at.
     */
    @Nullable
    public Direction hitFace(BlockPos pos, Vec3 origin, Vec3 direction) {
        return rayBoxFace(origin, direction, toAABB(pos));
    }

    /**
     * The face of {@code box} a ray from {@code origin} toward {@code direction} enters through,
     * or {@code null} if it misses (or {@code origin} is already inside, which shouldn't happen
     * in practice - the player has to be standing back far enough to have the sensor block
     * itself in view to have armed it in the first place).
     */
    @Nullable
    private static Direction rayBoxFace(Vec3 origin, Vec3 direction, AABB box) {
        double[] o = {origin.x, origin.y, origin.z};
        double[] d = {direction.x, direction.y, direction.z};
        double[] bMin = {box.minX, box.minY, box.minZ};
        double[] bMax = {box.maxX, box.maxY, box.maxZ};
        Direction[] negFace = {Direction.WEST, Direction.DOWN, Direction.NORTH};
        Direction[] posFace = {Direction.EAST, Direction.UP, Direction.SOUTH};

        double tMin = Double.NEGATIVE_INFINITY;
        double tMax = Double.POSITIVE_INFINITY;
        Direction hitFace = null;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-8) {
                if (o[i] < bMin[i] || o[i] > bMax[i]) {
                    return null;
                }
                continue;
            }
            double t1 = (bMin[i] - o[i]) / d[i];
            double t2 = (bMax[i] - o[i]) / d[i];
            Direction f1 = negFace[i];
            Direction f2 = posFace[i];
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
                Direction ftmp = f1;
                f1 = f2;
                f2 = ftmp;
            }
            if (t1 > tMin) {
                tMin = t1;
                hitFace = f1;
            }
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
                return null;
            }
        }
        return tMin >= 0 ? hitFace : null;
    }
}
