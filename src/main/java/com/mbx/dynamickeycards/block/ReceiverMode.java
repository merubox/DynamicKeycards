package com.mbx.dynamickeycards.block;

/**
 * How a receiver turns its bound {@link SignalSource}'s cached value into its own physical
 * redstone output: {@link #PULSE} mirrors the cached value in real time (plus an optional release
 * delay after it drops back to 0); {@link #TOGGLE} flips its own output only on a 0-to-nonzero
 * transition of the cached value - the falling direction is never treated as an edge (see
 * {@code ReceiverBlockEntity#tick}'s own doc for why). Set from the wrench UI's two mode buttons
 * ({@code menu.ReceiverScreen}).
 */
public enum ReceiverMode {
    PULSE,
    TOGGLE;

    /** Falls back to {@link #PULSE} for an unrecognized or missing name (e.g. corrupt NBT). */
    public static ReceiverMode byName(String name) {
        for (ReceiverMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return PULSE;
    }
}
