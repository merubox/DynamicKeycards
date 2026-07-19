package com.mbx.dynamickeycards.block;

import net.minecraft.util.StringRepresentable;

/**
 * Visual state of a card duplicator: idle, blinking green while waiting for the blank
 * target card, solid green after a successful copy, red after a rejected input.
 */
public enum DuplicatorMode implements StringRepresentable {
    IDLE("idle"),
    ARMED("armed"),
    COMPLETE("complete"),
    DENIED("denied");

    private final String name;

    DuplicatorMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
