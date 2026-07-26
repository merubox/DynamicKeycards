package com.mbx.dynamickeycards.compat.create;

import com.mbx.dynamickeycards.block.CardReaderBlockEntity;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Gateway between {@link CardReaderBlockEntity} and Create's Redstone Link network. Create is
 * an optional runtime dependency (compileOnly), so every call here is guarded by
 * {@link #isLoaded()} and every Create type stays inside this package — callers only ever see
 * an {@code Object} handle, never {@code IRedstoneLinkable} or {@code Create} directly, so
 * their own classes never force those types to resolve when Create isn't installed.
 */
public final class CreateLinkCompat {

    private CreateLinkCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("create");
    }

    /** Builds the adapter once per reader BlockEntity; store the result as an opaque Object. */
    public static Object createAdapter(CardReaderBlockEntity reader) {
        return new CardReaderLinkAdapter(reader);
    }

    public static void register(Level level, Object adapter) {
        com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, (CardReaderLinkAdapter) adapter);
    }

    public static void unregister(Level level, Object adapter) {
        com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, (CardReaderLinkAdapter) adapter);
    }

    /** Call after the transmitted strength or frequency key changes so the network re-scans it. */
    public static void notifyChanged(Level level, Object adapter) {
        com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, (CardReaderLinkAdapter) adapter);
    }
}
