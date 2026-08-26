package com.mbx.dynamickeycards.compat.create;

import net.neoforged.fml.ModList;

/**
 * Whether Create is installed - and nothing else.
 *
 * <p><b>Never add another member to this class, and never call {@code CreateLinkCompat} directly
 * to check.</b> A class links as a unit the moment it is actively used, so one method referencing
 * a missing Create type takes the whole class down even if only {@link #isLoaded()} is called -
 * which is exactly how a Create-less install used to crash on mod load, and again on opening the
 * config screen. See {@code ROADMAP.md}, "Create 부재 시 클래스로딩 크래시", for the full
 * investigation.
 */
public final class CreateAvailability {

    private CreateAvailability() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("create");
    }
}
