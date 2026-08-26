package com.mbx.dynamickeycards.block;

import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The "armed, awaiting its confirming second click" timer behind every wrench pickup in this mod.
 * Picking a device up is destructive enough to want a warning first, so the first sneak-click only
 * arms this and the second one - if it lands within {@link #PENDING_CONFIRM_TICKS} - goes through.
 *
 * <p>Every block entity that can be wrench-picked owns one of these ({@code CardReaderBlockEntity},
 * {@code MotionSensorBlockEntity}, {@code CardDuplicatorBlockEntity}, {@code ReceiverBlockEntity},
 * {@code TransmitterBlockEntity}), and the card reader owns a second one for its golden-keycard
 * full reset, which needs exactly the same confirm-then-act shape.
 *
 * <p>Deliberately not persisted: a confirmation left armed across a save/reload reads as expired,
 * which is the safe direction - a destructive action should only ever fire right after its own
 * warning, not an hour later.
 */
final class WrenchPickupState {

    /** Three seconds - long enough to react to the warning, short enough not to linger. */
    private static final int PENDING_CONFIRM_TICKS = 60;

    /** Game time {@link #arm} last ran; {@code -1} while nothing is awaiting confirmation. */
    private long armedAtGameTime = -1;

    /**
     * Whether a confirmation is armed and still inside its window. A {@code null} level (a block
     * entity not attached to a world yet) reads as "not pending" rather than throwing - there is
     * no game time to compare against, and nothing could have armed it either.
     */
    boolean isPending(@Nullable Level level) {
        return armedAtGameTime >= 0 && level != null
                && level.getGameTime() - armedAtGameTime < PENDING_CONFIRM_TICKS;
    }

    /** Starts the confirmation window; a second click within {@link #PENDING_CONFIRM_TICKS} confirms. */
    void arm(@Nullable Level level) {
        armedAtGameTime = level != null ? level.getGameTime() : -1;
    }

    /**
     * Cancels an armed confirmation outright - same end state as letting it time out, but
     * immediate. Called whenever the player does something else with the device that isn't the
     * confirming click itself (opening its config UI, changing a mode), on the theory that a
     * destructive confirmation should only ever fire right after its own warning.
     */
    void clear() {
        armedAtGameTime = -1;
    }
}
