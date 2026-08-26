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
public interface LinkDeviceBlockEntity extends WrenchPickupTarget {

    SignalMode getSignalMode();

    void setSignalMode(SignalMode mode);

    /**
     * Whether the three mode buttons do anything right now. Always true except for a sensor bound
     * to *another sensor*, where the sensor it's bound to already owns the actual signal
     * mode - only the signal length (its own hold delay) stays independently adjustable there.
     * A sensor bound to a *reader* is a special case that still returns true here (see
     * {@code AdvancedSensorBlockEntity}'s own override): the three buttons stay clickable, just
     * repurposed to its own {@code BoundReaderMode} set instead of {@link SignalMode} - see
     * {@link #isFrequencyEditable} for why that's a separate question from this one.
     */
    default boolean isLinkModeEditable() {
        return true;
    }

    /**
     * Whether the frequency ghost slots specifically can currently be edited - separate from
     * {@link #isLinkModeEditable} because a sensor bound to a reader keeps its mode buttons live
     * (repurposed, see {@link #isLinkModeEditable}'s own doc) while the frequency slots stay
     * locked regardless: Create Link involvement is off entirely in every bound state, reader or
     * sensor, so there's never a meaningful frequency to set. Defaults to mirroring
     * {@link #isLinkModeEditable} - the two only diverge for that one case.
     */
    default boolean isFrequencyEditable() {
        return isLinkModeEditable();
    }

    ItemStack getFrequencySlot(int index);

    void setFrequencySlot(int index, ItemStack stack);

    int getSignalLength();

    void setSignalLength(int ticks);

    void clearSignalLength();

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
