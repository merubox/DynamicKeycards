package com.mbx.dynamickeycards.compat.accessories;

import net.neoforged.fml.ModList;

/**
 * Whether the Accessories mod is installed, and <b>nothing else</b> - see
 * {@code CuriosAvailability} for why this is its own memberless class.
 */
public final class AccessoriesAvailability {

    private static final boolean LOADED = ModList.get().isLoaded("accessories");

    private AccessoriesAvailability() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }
}
