package com.mbx.dynamickeycards.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block entity with a wrench-opened {@code LinkDeviceScreen}: a {@link SignalMode} toggle,
 * two Create Redstone Link frequency slots, and a configurable signal-length number box. Both
 * {@link CardReaderBlockEntity} and {@code MotionSensorBlockEntity} implement this - the menu
 * and screen classes only ever talk to devices through it, never a concrete block entity type,
 * so the same UI serves every device without knowing which one it's open for.
 *
 * <p>What the length number actually controls is left entirely to the implementor: for the
 * reader it's how long the accept pulse fires, for a motion sensor it's how long the signal
 * lingers after the last detected entity leaves. Only the storage and UI plumbing are shared.
 *
 * <p>{@link #getLevel()}, {@link #getBlockPos()} and {@link #getBlockState()} aren't declared
 * here for their own sake - every implementor is already a {@code BlockEntity}, which provides
 * matching concrete methods, so they're satisfied for free and only listed so the menu/screen
 * can call them through this interface without needing the concrete type.
 */
public interface LinkDeviceBlockEntity {

    SignalMode getSignalMode();

    void setSignalMode(SignalMode mode);

    /**
     * Whether {@link #setSignalMode} and the frequency slots can currently be changed. Always
     * true except for a bound advanced sensor, where the reader it's bound to already owns the
     * actual signal mode/frequency - only the signal length (its own hold delay) stays
     * independently adjustable there. {@code LinkDeviceMenu}/{@code LinkDeviceScreen} check this
     * to lock out and grey those controls without needing to know why.
     */
    default boolean isLinkModeEditable() {
        return true;
    }

    ItemStack getFrequencySlot(int index);

    void setFrequencySlot(int index, ItemStack stack);

    int getSignalLength();

    void setSignalLength(int ticks);

    void clearSignalLength();

    /** Whether a sneak-wrench pickup ({@code WrenchConfigurableBlock}) is awaiting its confirming click. */
    boolean isWrenchPickupPending();

    /** Arms the sneak-wrench pickup confirmation. */
    void armWrenchPickupPending();

    /**
     * Cancels any armed destructive-action confirmation on this device (wrench pickup, and
     * anything else an implementor adds - the reader also uses this for its own reset
     * confirmation). Called whenever the player does something else instead of following
     * through, on the theory that a confirmation should only ever fire right after its warning.
     */
    void clearPendingActions();

    /**
     * Strength (0 or 15) this device currently wants to transmit over Create's Redstone Link -
     * for the reader, whether the accept pulse is running; for a motion sensor, whether it
     * currently detects anything. Only consulted while {@link SignalMode#linkActive}.
     */
    int getLinkStrength();

    Level getLevel();

    BlockPos getBlockPos();

    BlockState getBlockState();

    boolean isRemoved();
}
