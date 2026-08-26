package com.mbx.dynamickeycards.compat.create;

import com.mbx.dynamickeycards.block.LinkDeviceBlockEntity;
import net.minecraft.world.level.Level;

/**
 * Gateway between any {@link LinkDeviceBlockEntity} (the card reader, or a motion sensor) and
 * Create's Redstone Link network. Create is an optional runtime dependency (compileOnly), so
 * every call here is guarded by {@link CreateAvailability#isLoaded} and every Create type stays
 * inside this package — callers only ever see an {@code Object} handle, never
 * {@code IRedstoneLinkable} or {@code Create} directly, so their own classes never force those
 * types to resolve when Create isn't installed.
 *
 * <p>The "is Create loaded" check itself deliberately does <em>not</em> live here - see
 * {@link CreateAvailability}'s own doc for why sharing a class with these Create-referencing
 * methods made it unsafe to call from anywhere that hadn't already linked this class some other
 * way.
 */
public final class CreateLinkCompat {

    private CreateLinkCompat() {
    }

    /** Builds the adapter once per device block entity; store the result as an opaque Object. */
    public static Object createAdapter(LinkDeviceBlockEntity device) {
        return new LinkAdapter(device);
    }

    public static void register(Level level, Object adapter) {
        com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, (LinkAdapter) adapter);
    }

    public static void unregister(Level level, Object adapter) {
        com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, (LinkAdapter) adapter);
    }

    /** Call after the transmitted strength or frequency key changes so the network re-scans it. */
    public static void notifyChanged(Level level, Object adapter) {
        com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, (LinkAdapter) adapter);
    }
}
