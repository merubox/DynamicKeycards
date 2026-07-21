package com.mbx.dynamickeycards;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common config: config/dynamickeycards-common.toml */
public class DKConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MAX_REGISTRATIONS_PER_READER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        MAX_REGISTRATIONS_PER_READER = builder
                .comment("Maximum number of keycards that can be registered on a single card reader.")
                .defineInRange("maxRegistrationsPerReader", 128, 1, 65536);
        SPEC = builder.build();
    }
}
