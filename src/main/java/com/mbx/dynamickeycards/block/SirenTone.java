package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.registry.DKSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * The voice of a running siren - the one sound in this mod that is not feedback. Everything the
 * card machines say is a single tone acknowledging something that just happened ({@code DKSounds});
 * this is a tone held for as long as the light turns, with its own pitch, cadence and distance,
 * and none of that has anything to say to the rest of the mod. Hence its own file, beside the
 * block it belongs to rather than in the shared vocabulary.
 *
 * <p>Both tiers are stitched together out of vanilla note block samples - see {@link #play} for
 * how, and {@code scripts/siren/README.md} and the ROADMAP for what the reference recordings
 * measured. No audio file ships with this mod; {@code sounds.json} points the two events at
 * vanilla's own oggs.
 *
 * <p>Server-side only. It sends its own packets, one per listener - see {@link #playFalloff}.
 */
public final class SirenTone {

    private SirenTone() {
    }

    /** What {@code note/bit} sounds at pitch 1 - F#4, measured off the file rather than taken on trust. */
    private static final float BIT_HZ = 369.99f;
    /** {@code note/flute} is sampled an octave up, which is the point of using it - see {@link #play}. */
    private static final float FLUTE_HZ = 739.99f;
    /** The engine's floor ({@code SoundEngine#calculatePitch}); a flute cannot be asked below F#4. */
    private static final float MIN_PITCH = 0.5f;
    /**
     * Ticks between retriggers, for both tiers. Every note block sample is a decaying one-shot -
     * the longest is under half a second - so a held tone has to be built by overlapping copies,
     * and how far apart they sit is the whole difference between a tone and a stutter. Measured
     * against the samples' own envelopes: at these pitches one tick leaves the sum wobbling by
     * about a quarter, two ticks by two thirds, three by more than double. Only one tick reads as
     * continuous.
     */
    private static final int SIREN_INTERVAL = 1;

    /**
     * How far each tier carries. Not vanilla's: {@link #playFalloff} shapes the fade itself, and
     * these are where it reaches zero. Set a little wider than the distance either is actually
     * audible at, because the curve arrives at zero flat - see {@link #FALLOFF_EXPONENT}.
     */
    public static final float CAUTION_RANGE = 20f;
    public static final float EMERGENCY_RANGE = 40f;
    /**
     * Shapes the fade: volume falls as {@code (1 - d/range)} raised to this. Vanilla leaves it at
     * 1, a straight line in amplitude - which arrives at the range still dropping at full slope,
     * and since hearing is logarithmic, that last stretch reads as the sound being switched off
     * rather than fading out. Squaring it spends the loudness earlier and lands on zero with the
     * slope already flat, so the tone thins away instead of stopping.
     */
    private static final float FALLOFF_EXPONENT = 2f;
    /** Below this the client would skip the sound anyway; not worth a packet. */
    private static final float MIN_AUDIBLE_VOLUME = 0.005f;

    /**
     * The car park buzzer, measured off the reference recording: a flat 240Hz that never wavers,
     * with a strong third harmonic and no second at all. That spectrum is a square wave, which is
     * what {@code note/bit} is - hence this tier's sample.
     */
    private static final float CAUTION_PITCH = 240f / BIT_HZ;
    /**
     * Per note, not per tone - a tick apart, about four copies are always sounding together. How
     * far the tone carries is {@link #CAUTION_RANGE}'s business, not this - this is the loudness
     * at the block itself, and the engine clamps it at 1, so this is as loud as it goes.
     */
    private static final float CAUTION_VOLUME = 1f;

    /**
     * One pass of the emergency wail, from the reference recording: it repeats every 2.0s exactly.
     * The pass is not a symmetric swell - it climbs fast, holds a long time, then falls away, and
     * the numbers below are where the recording's own envelope turns.
     */
    private static final int WAIL_TICKS = 40;
    /** 0.35s of upward glide before it settles. */
    private static final int WAIL_RISE_TICKS = 7;
    /** 1.5s in, the hold gives way and the tone falls off. */
    private static final int WAIL_DECAY_FROM = 30;
    /** Silent for the last 0.1s, then the next pass opens. */
    private static final int WAIL_SILENT_FROM = 38;
    /**
     * The glide settles on 501Hz, measured. It starts near 310Hz in the recording, but a flute
     * sampled at F#5 cannot be pitched that low - {@link #MIN_PITCH} lands on 370Hz instead, which
     * costs the first fraction of a second of the climb and nothing else.
     */
    private static final float WAIL_START_PITCH = MIN_PITCH;
    private static final float WAIL_HOLD_PITCH = 501f / FLUTE_HZ;
    /** How loud the glide opens, as a share of the hold. */
    private static final float WAIL_ATTACK_GAIN = 0.25f;
    /** See {@link #CAUTION_VOLUME}. It still lands well above the buzz: {@code flute} is twice the file. */
    private static final float EMERGENCY_VOLUME = 1f;
    /**
     * The wail is laid down twice, an octave apart. {@code flute} is very nearly a sine - the one
     * thing it does not have is the second harmonic the reference leans on, which is as strong as
     * the fundamental for much of the pass. Doubling the tone at twice the frequency puts it back
     * without bringing in a second sample's harder onset. This is that layer's share of the volume.
     */
    private static final float WAIL_HARMONIC_GAIN = 0.45f;

    /**
     * A running siren's own voice, one tick's worth - call every tick while it turns.
     *
     * <p>The buzz is {@code bit}: the reference is a fundamental and a third harmonic with no
     * second, which is a square wave, which is what that sample is.
     *
     * <p>The wail is {@code flute}, picked for its attack. Pitch speeds playback up, so a sample
     * asked to sound above its own note leaves less tail to overlap with - and {@code flute} is
     * sampled an octave above the others, so 501Hz asks it to play <em>slower</em>, stretching its
     * tail to nearly seven ticks where {@code pling} managed four and a half. Its onset is the
     * gentler half of it: 20ms against {@code pling}'s 0.6ms. A hard onset repeated every tick is
     * a 20Hz click train sitting under the tone, which is exactly what it sounded like.
     *
     * <p>{@code elapsedTicks} counts from the moment this siren started turning, so each one opens
     * its cycle at the beginning instead of joining whatever phase a shared world clock was in.
     * It is deliberately not the animation's clock: at the fastest turn speed the light comes
     * round five times a second, and a siren keeping time with it would be unbearable.
     */
    public static void play(ServerLevel level, BlockPos pos, SirenMode mode, long elapsedTicks) {
        if (Math.floorMod(elapsedTicks, SIREN_INTERVAL) != 0) {
            return;
        }
        switch (mode) {
            case MUTE -> {
            }
            case CAUTION -> playFalloff(level, pos, DKSoundEvents.SIREN_CAUTION,
                    CAUTION_VOLUME, CAUTION_PITCH, CAUTION_RANGE);
            case EMERGENCY -> emergency(level, pos, elapsedTicks);
        }
    }

    /** A high wail that glides up, holds, falls away, and starts again - see {@link #WAIL_TICKS}. */
    private static void emergency(ServerLevel level, BlockPos pos, long elapsedTicks) {
        int t = (int) Math.floorMod(elapsedTicks, WAIL_TICKS);
        if (t >= WAIL_SILENT_FROM) {
            return;
        }
        float pitch = WAIL_HOLD_PITCH;
        float gain = 1f;
        if (t < WAIL_RISE_TICKS) {
            float climb = t / (float) WAIL_RISE_TICKS;
            pitch = Mth.lerp(climb, WAIL_START_PITCH, WAIL_HOLD_PITCH);
            gain = Mth.lerp(climb, WAIL_ATTACK_GAIN, 1f);
        } else if (t >= WAIL_DECAY_FROM) {
            gain = 1f - (t - WAIL_DECAY_FROM) / (float) (WAIL_SILENT_FROM - WAIL_DECAY_FROM);
        }
        Holder<SoundEvent> voice = DKSoundEvents.SIREN_EMERGENCY;
        playFalloff(level, pos, voice, EMERGENCY_VOLUME * gain, pitch, EMERGENCY_RANGE);
        playFalloff(level, pos, voice, EMERGENCY_VOLUME * gain * WAIL_HARMONIC_GAIN,
                pitch * 2f, EMERGENCY_RANGE);
    }

    /**
     * Sends one tone with our own distance curve instead of vanilla's.
     *
     * <p>{@code Level#playSound} already sends a separate packet to every player in range, so
     * doing that by hand costs nothing extra and buys the volume per listener - which is the only
     * way to change the shape of the fade at all. The engine's own linear attenuation still
     * multiplies in on top, but {@code sounds.json} sets that distance far wider than these
     * ranges, so over the stretch that matters it is nearly flat and this curve is what is heard.
     */
    private static void playFalloff(ServerLevel level, BlockPos pos, Holder<SoundEvent> sound,
                                    float volume, float pitch, float range) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        long seed = level.getRandom().nextLong();
        for (ServerPlayer player : level.players()) {
            double distanceSq = player.distanceToSqr(x, y, z);
            if (distanceSq >= range * range) {
                continue;
            }
            float taper = 1f - (float) Math.sqrt(distanceSq) / range;
            float shaped = volume * (float) Math.pow(taper, FALLOFF_EXPONENT);
            if (shaped < MIN_AUDIBLE_VOLUME) {
                continue;
            }
            player.connection.send(new ClientboundSoundPacket(
                    sound, SoundSource.BLOCKS, x, y, z, shaped, pitch, seed));
        }
    }
}
