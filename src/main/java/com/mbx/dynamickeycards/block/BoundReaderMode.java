package com.mbx.dynamickeycards.block;

/**
 * How a sensor bound to a reader ({@code AdvancedSensorBlockEntity}) splits its detection between
 * its own local signal and the reader it drives - see {@code ROADMAP.md}'s mode-naming principle.
 * Deliberately unrelated to {@link SignalMode} (which governs physical wire vs Create Redstone
 * Link): a bound sensor's Create Link involvement is off entirely no matter which of these is
 * picked, so setting one of these never touches {@code LinkDeviceState}/{@code CreateLinkCompat}
 * at all - see {@code AdvancedSensorBlockEntity#setBoundReaderMode}.
 */
public enum BoundReaderMode {
    /**
     * Both this sensor's own local signal and the bound reader trigger together, timed by this
     * sensor's own release delay (the reader's pulse is marked externally-originated and held
     * open for exactly as long as this sensor keeps detecting) - the default, and how a bound
     * sensor has always behaved before this mode even existed.
     */
    SENSOR_CENTRIC_SIMULTANEOUS,
    /** Only the bound reader triggers - this sensor's own local signal stays off (still honors an external hold from something else bound to *this* sensor, if any). */
    READER_ONLY,
    /**
     * Both trigger, but with fully independent timing: this sensor's own local signal follows its
     * own release delay as usual, while the reader's pulse is *not* marked externally-originated,
     * so it manages its own release using its own configured signal length instead of following
     * this sensor's hold.
     */
    SIMULTANEOUS;

    /** Falls back to {@link #SENSOR_CENTRIC_SIMULTANEOUS} for an unrecognized or missing name (e.g. corrupt NBT). */
    public static BoundReaderMode byName(String name) {
        for (BoundReaderMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return SENSOR_CENTRIC_SIMULTANEOUS;
    }
}
