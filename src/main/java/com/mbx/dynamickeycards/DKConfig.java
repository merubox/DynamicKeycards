package com.mbx.dynamickeycards;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common config: config/dynamickeycards-common.toml */
public class DKConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MAX_REGISTRATIONS_PER_READER;
    public static final ModConfigSpec.IntValue DEFAULT_PULSE_LENGTH_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        MAX_REGISTRATIONS_PER_READER = builder
                .comment("Maximum number of keycards that can be registered on a single card reader.")
                .defineInRange("maxRegistrationsPerReader", 128, 1, 65536);
        DEFAULT_PULSE_LENGTH_TICKS = builder
                .comment("Default redstone pulse length of card readers, in ticks (20 ticks = 1 second).",
                        "Individual readers can override this per block; this value applies to every",
                        "reader without its own override.")
                .defineInRange("defaultPulseLengthTicks", 60, 1, 72000);
        SPEC = builder.build();
    }
}
