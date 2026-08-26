package com.mbx.dynamickeycards.block;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side-only, non-persistent mirror of just the position half of {@link DeviceIndex} -
 * populated from {@code CardReaderBlockEntity}/{@code MotionSensorBlockEntity}'s own
 * {@code onLoad}/removal (which already runs on both sides), so a held {@code BoundSensorBlockItem}/
 * {@code LinkedReaderBlockItem}'s stored {@link UUID} target can still be resolved to a
 * {@link BlockPos} for the client-only "highlight my bound target" rendering ({@code DKClientEvents}) -
 * that rendering has no business asking the server, and only ever needs the position of a device
 * whose chunk the client already has loaded anyway (the same range the highlight itself is capped
 * to). Never written to or read from off the client thread.
 */
public final class ClientDeviceCache {

    private static final Map<UUID, BlockPos> POSITIONS = new HashMap<>();

    private ClientDeviceCache() {
    }

    public static void register(UUID id, BlockPos pos) {
        POSITIONS.put(id, pos);
    }

    public static void unregister(UUID id) {
        POSITIONS.remove(id);
    }

    @Nullable
    public static BlockPos getPosition(UUID id) {
        return POSITIONS.get(id);
    }
}
