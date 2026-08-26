package com.mbx.dynamickeycards.block;

/**
 * How a transmitter accepts input: {@link #REDSTONE_ONLY} reads only the physical redstone signal
 * reaching it from any side; {@link #MIXED} reads that too, but also lets an empty-hand click while standing
 * act as a second, independent trigger - either one raises the broadcast. Set from the wrench UI's
 * two mode buttons ({@code menu.TransmitterScreen}).
 *
 * <p>"Mixed" is the exact word {@code ROADMAP.md}'s naming principle reserves for this: a choice
 * between several input methods (either one triggers), as opposed to "simultaneous", which means
 * two outputs that always fire together - see {@link SignalMode#SIMULTANEOUS}.
 */
public enum TransmitterMode {
    REDSTONE_ONLY,
    MIXED;

    /** Falls back to {@link #REDSTONE_ONLY} for an unrecognized or missing name (e.g. corrupt NBT). */
    public static TransmitterMode byName(String name) {
        for (TransmitterMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        // 0.1.8-dev builds before the rename wrote this mode as "MANUAL_MIXED"
        if ("MANUAL_MIXED".equals(name)) {
            return MIXED;
        }
        return REDSTONE_ONLY;
    }
}
