package com.mbx.dynamickeycards.block;

/**
 * How loud a siren is while it spins: {@link #MUTE} is light only, {@link #CAUTION} a low
 * intermittent tone, {@link #EMERGENCY} the full alarm. Only the volume tier changes - the light
 * itself behaves identically in all three, so a muted siren is still a working warning light.
 * Set from the wrench UI's three mode buttons ({@code menu.SirenScreen}).
 *
 * <p>{@link SirenTone} is where the two tones are actually built; {@code SirenBlockEntity} asks
 * {@link #audible} first so a muted siren costs nothing.
 */
public enum SirenMode {
    MUTE,
    CAUTION,
    EMERGENCY;

    /** Whether this tier makes any sound at all - the one thing the rest of the code needs today. */
    public boolean audible() {
        return this != MUTE;
    }

    /** Falls back to {@link #MUTE} for an unrecognized or missing name (e.g. corrupt NBT). */
    public static SirenMode byName(String name) {
        for (SirenMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return MUTE;
    }
}
