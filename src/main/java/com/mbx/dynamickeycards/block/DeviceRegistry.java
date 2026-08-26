package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DKNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The bookkeeping every addressable device shares: persisting its {@link UUID}, putting it into
 * {@link DeviceIndex} (server) or {@link ClientDeviceCache} (client) as chunks load, and sorting
 * out the case where two devices turn up holding the same id.
 *
 * <p>Each device keeps its own {@code deviceId} field - this is the flow around it, not the
 * value itself, since a device's id is read all over its own class.
 */
final class DeviceRegistry {

    private static final String TAG_KEY = "DeviceId";

    private DeviceRegistry() {
    }

    /** Writes the device id, in the one place its NBT key is spelled out. */
    static void save(CompoundTag tag, UUID deviceId) {
        tag.putUUID(TAG_KEY, deviceId);
    }

    /** Reads the device id back, minting a fresh one if this device has never had one saved. */
    static UUID load(CompoundTag tag) {
        return tag.hasUUID(TAG_KEY) ? tag.getUUID(TAG_KEY) : UUID.randomUUID();
    }

    /**
     * Chunk-load registration, for {@code onLoad}. Registers into whichever side's lookup applies.
     *
     * @return the server-side index, so a caller with more registering to do can reuse it, or
     *         {@code null} on the client (or before a level is attached at all)
     */
    @Nullable
    static DeviceIndex registerOnLoad(BlockEntity be, UUID deviceId) {
        Level level = be.getLevel();
        if (level instanceof ServerLevel serverLevel) {
            DeviceIndex index = DeviceIndex.get(serverLevel);
            index.register(deviceId, be.getBlockPos());
            return index;
        }
        if (level != null && level.isClientSide) {
            ClientDeviceCache.register(deviceId, be.getBlockPos());
        }
        return null;
    }

    /**
     * Registration for {@code loadAdditional}, resolving a duplicate id along the way. NBT can be
     * pasted onto an already-placed block - a Create schematic print, most notably - which can
     * leave two live devices claiming the same id.
     *
     * <p>When that happens this device takes a fresh id and the old one is recorded as superseded,
     * both server-side ({@link DeviceIndex#recordSuperseded}) and client-side (a
     * {@code DeviceSupersededPayload} broadcast, since {@code DeviceIndex#resolve} alone only
     * fixes the server). Nothing that <em>referenced</em> the old id is rewritten here: each such
     * reference follows the supersede lazily the next time it is looked up, which lands on this
     * copy's own printed sibling when the reference was part of the same print and on the same
     * real device otherwise - without this code having to tell those two cases apart.
     *
     * @param onSuperseded device-specific state to reset when the id changed - a cached signal
     *                     value keyed to the old identity, typically. {@code null} for devices
     *                     with nothing to reset.
     * @return the id to keep: the original one, or a fresh one if the original was a duplicate
     */
    static UUID registerResolvingDuplicate(BlockEntity be, ServerLevel level, DeviceIndex index,
                                           UUID deviceId, @Nullable Runnable onSuperseded) {
        UUID id = deviceId;
        if (index.isDuplicate(level, id, be.getBlockPos())) {
            UUID oldId = id;
            id = UUID.randomUUID();
            index.recordSuperseded(oldId, id);
            DKNetwork.broadcastSuperseded(level, oldId, be.getBlockPos());
            if (onSuperseded != null) {
                onSuperseded.run();
            }
            be.setChanged();
        }
        index.register(id, be.getBlockPos());
        return id;
    }
}
