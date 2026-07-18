package com.mbx.dynamickeycards.block;

import net.minecraft.util.StringRepresentable;

/**
 * Visual state of a card reader's front panel: idle, green while the accept pulse runs,
 * red after a rejected keycard, register display while register mode is armed.
 */
public enum CardReaderMode implements StringRepresentable {
    OFF("off"),
    ACCEPTED("accepted"),
    DENIED("denied"),
    REGISTER("register");

    private final String name;

    CardReaderMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
