package com.mbx.dynamickeycards.client;

import net.minecraft.util.Mth;

/**
 * A value that eases toward whatever it's last targeted over a fixed duration, capturing
 * wherever it currently is and continuing from there rather than restarting from scratch when
 * the target changes again mid-ease - the sensor detection-range highlight uses one of these for
 * its box size and a second, separate one for its line width, which used to be two independent,
 * hand-duplicated copies of this exact capture/lerp logic before being pulled out here.
 */
public final class Eased<T> {

    /** Interpolates between two values of {@code T} - e.g. {@code Mth::lerp} for a float, or a small lambda for an {@link net.minecraft.world.phys.AABB}. */
    @FunctionalInterface
    public interface Lerp<T> {
        T lerp(T from, T to, float t);
    }

    private final long durationNanos;
    private final Lerp<T> lerpFn;
    private T from;
    private T to;
    private long startNanos = System.nanoTime();

    public Eased(long durationNanos, Lerp<T> lerpFn, T initial) {
        this.durationNanos = durationNanos;
        this.lerpFn = lerpFn;
        this.from = initial;
        this.to = initial;
    }

    /**
     * Retargets toward {@code target} (capturing the current interpolated value as the new
     * starting point) if it's different from the current target, then returns the current eased
     * value. {@code T} must implement {@link Object#equals} meaningfully for the retarget check
     * to work - a plain {@code record} or a type like {@link net.minecraft.world.phys.AABB} that
     * compares by value both qualify.
     */
    public T towards(T target) {
        long now = System.nanoTime();
        if (!target.equals(to)) {
            from = current(now);
            to = target;
            startNanos = now;
        }
        return current(now);
    }

    /** Jumps straight to {@code target}, no easing - for cases where interpolating wouldn't make sense (e.g. the target object itself changed, not just its value). */
    public void snapTo(T target) {
        from = target;
        to = target;
        startNanos = System.nanoTime();
    }

    private T current(long now) {
        float t = Mth.clamp((now - startNanos) / (float) durationNanos, 0f, 1f);
        return lerpFn.lerp(from, to, t);
    }
}
