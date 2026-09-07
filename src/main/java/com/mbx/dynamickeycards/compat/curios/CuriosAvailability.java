package com.mbx.dynamickeycards.compat.curios;

import net.neoforged.fml.ModList;

/**
 * Whether Curios is installed, and <b>nothing else</b>.
 *
 * <p>Deliberately has no other member: a class is linked as one unit, so a single method
 * mentioning a Curios type would make even this check fail to link when Curios is absent. Every
 * caller outside {@code compat/curios} asks here and never touches {@link CuriosKeycards}
 * directly - see {@code CreateAvailability}, which exists for the same reason after that exact
 * mistake crashed the mod (the investigation is in ROADMAP.md).
 */
public final class CuriosAvailability {

    private static final boolean LOADED = ModList.get().isLoaded("curios");

    private CuriosAvailability() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }
}
