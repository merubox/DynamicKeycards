package com.mbx.dynamickeycards.block;

/**
 * How a device's redstone output leaves the block: over the physical redstone wire, over
 * Create's Redstone Link (see {@code compat.create.CreateLinkCompat}), or both. Set from the
 * wrench UI's three mode buttons ({@code LinkDeviceScreen}). Shared by every block that
 * implements {@code LinkDeviceBlockEntity} - the card reader and both motion sensors.
 */
public enum SignalMode {
    NORMAL(true, false),
    LINK(false, true),
    MIXED(true, true);

    public final boolean physicalActive;
    public final boolean linkActive;

    SignalMode(boolean physicalActive, boolean linkActive) {
        this.physicalActive = physicalActive;
        this.linkActive = linkActive;
    }

    /** Falls back to {@link #NORMAL} for an unrecognized or missing name (e.g. corrupt NBT). */
    public static SignalMode byName(String name) {
        for (SignalMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return NORMAL;
    }
}
