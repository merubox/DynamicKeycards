package com.mbx.dynamickeycards.block;

/**
 * A block entity that can be picked up with a wrench after a confirming second click - the card
 * reader, both motion sensors, the card duplicator, the receiver, and the transmitter.
 *
 * <p>Just the confirm-timer contract, so {@link WrenchPickupBlock#wrenchPickup} can drive any of
 * them without knowing which device it holds. {@link LinkDeviceBlockEntity} extends this, since
 * every link device is also wrench-pickable; the duplicator/receiver/transmitter implement it
 * directly, having nothing else to do with Create's Redstone Link.
 *
 * <p>Implementations hold the actual timer in a {@link WrenchPickupState}.
 */
public interface WrenchPickupTarget {

    /** Whether a pickup is armed and still inside its confirmation window. */
    boolean isWrenchPickupPending();

    /** Arms the confirmation - the next sneak-wrench click within the window completes the pickup. */
    void armWrenchPickupPending();

    /**
     * Cancels any armed confirmation on this device - plural because
     * {@link CardReaderBlockEntity#clearPendingActions} also clears its golden-keycard reset.
     */
    void clearPendingActions();
}
