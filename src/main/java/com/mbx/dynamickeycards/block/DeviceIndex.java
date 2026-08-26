package com.mbx.dynamickeycards.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Level-wide address book: {@code deviceId -> (position, last-reported signal strength)}. Lets
 * every device that participates in this mod's own wireless system ({@link SignalSource}
 * implementors, and anything that binds to one, like a receiver or a linked reader) refer to each
 * other by a stable id instead of a raw {@link BlockPos} - a coordinate captured once can go
 * stale (the device moves, or something unrelated gets placed at the same spot later); an id
 * either still resolves to the real device or doesn't resolve at all.
 *
 * <p>The cached signal value exists specifically so a consumer's per-tick read (see
 * {@code ReceiverBlockEntity}) never has to touch the source's actual {@code BlockEntity} - only
 * a plain map lookup, regardless of whether the source's own chunk happens to be loaded right
 * now. A source only ever calls {@link #updateSignal} when its own value actually changes (see
 * each {@link SignalSource} implementor's own tick), so this stays cheap - no per-tick writes
 * from a source that hasn't changed.
 */
public class DeviceIndex extends SavedData {

    private static final String DATA_NAME = "dynamickeycards_device_index";

    private final Map<UUID, BlockPos> positions = new HashMap<>();
    private final Map<UUID, Integer> signals = new HashMap<>();
    /**
     * {@code old id -> new id}, recorded whenever a device turns out to collide with one that
     * already exists elsewhere (see {@link #isDuplicate}/{@link #recordSuperseded}) - almost always
     * a Create schematic print duplicating a captured device's saved NBT onto a new copy while the
     * original still stands. Persisted (a stale reference baked into some *other* device's own
     * saved NBT - {@code linkedReaderIds}, {@code boundReader}, {@code boundSource}, ... - needs to
     * keep resolving correctly across restarts too, not just until the next reload) and consulted
     * by every id lookup (see {@link #resolve}), so a reference to a superseded id transparently
     * follows the chain to whatever it became - without this, every cross-reference into a
     * schematic-duplicated device would need to be found and rewritten individually, and any of
     * them captured from *outside* the print (not itself superseded) would need to be told apart
     * from the ones that need remapping; resolving lazily at lookup time sidesteps both problems -
     * an unaffected reference simply has no entry here and resolves to itself, unchanged.
     */
    private final Map<UUID, UUID> supersededBy = new HashMap<>();
    /**
     * Not persisted - purely a runtime index, rebuilt as each {@code ReceiverBlockEntity}
     * re-registers itself (see {@link #registerReceiver}), same self-healing pattern as
     * {@link #positions} itself. Lets {@link #updateSignal} push a change straight to whichever
     * receivers are bound to that source the instant it happens, instead of every receiver having
     * to wait out its own next tick to poll {@link #getSignal} and notice.
     */
    private final Map<UUID, Set<ReceiverBlockEntity>> receiversBySource = new HashMap<>();

    public static DeviceIndex get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DeviceIndex::new, DeviceIndex::load, null), DATA_NAME);
    }

    /** Called from a device's own {@code onLoad}/{@code setPlacedBy} - both placement and every reload re-register (self-healing). */
    public void register(UUID id, BlockPos pos) {
        BlockPos previous = positions.put(id, pos);
        if (!pos.equals(previous)) {
            setDirty();
        }
    }

    /** Called only when a device is actually destroyed (not moved) - see each caller's own {@code onRemove}. */
    public void unregister(UUID id) {
        boolean removedPos = positions.remove(id) != null;
        boolean removedSignal = signals.remove(id) != null;
        receiversBySource.remove(id);
        if (removedPos || removedSignal) {
            setDirty();
        }
    }

    /**
     * Records {@code id}'s current raw signal strength (0-15) - a no-op if unchanged, so callers
     * can freely call this every time their own value might have changed without worrying about
     * spamming saves. Silently ignored for an id that was never {@link #register}ed (nothing to
     * attach the value to yet). On an actual change, immediately pushes it to every receiver
     * currently bound to {@code id} (see {@link #registerReceiver}) - {@code level} is needed for
     * that push (a bound receiver reacts as if its own tick just ran).
     */
    public void updateSignal(UUID id, int value, ServerLevel level) {
        if (!positions.containsKey(id)) {
            return;
        }
        Integer previous = signals.put(id, value);
        if (previous != null && previous == value) {
            return;
        }
        setDirty();
        notifyReceivers(id, level);
    }

    /** Resolves {@code id} through {@link #supersededBy} to whatever it ultimately became - see that field's own doc. Cycle-safe (there's no legitimate way for a chain to loop, but a corrupt save shouldn't be able to hang this). */
    private UUID resolve(UUID id) {
        UUID current = id;
        Set<UUID> seen = null;
        while (true) {
            UUID next = supersededBy.get(current);
            if (next == null) {
                return current;
            }
            if (seen == null) {
                seen = new HashSet<>();
            }
            if (!seen.add(current)) {
                return current;
            }
            current = next;
        }
    }

    @Nullable
    public BlockPos getPosition(UUID id) {
        return positions.get(resolve(id));
    }

    /**
     * Whether {@code id} genuinely collides with a device that already exists somewhere else -
     * called from a device's own {@code loadAdditional} (only ever reached with a non-null
     * {@code level} when NBT is being pasted onto an already-placed block, e.g. a Create schematic
     * print - see each {@code resolveDuplicateIdentity} override's own doc for why {@code loadAdditional}
     * specifically, not {@code onLoad}). A stale record pointing elsewhere is NOT by itself proof of
     * a duplicate: a genuine Create contraption move leaves exactly this kind of stale entry behind
     * too (the original position's block is truly gone, picked up into the contraption, not merely
     * unregistered) - so this only reports a collision if a block entity still actually exists at
     * that other position right now. If that position's chunk isn't currently loaded, this can't
     * tell the two apart without force-loading it, and defaults to "not a duplicate" - the safer
     * direction to be wrong in, since wrongly treating a real move as a duplicate would sever a
     * link that was never actually broken, while missing a real duplicate merely leaves this one
     * pre-existing edge case unhandled. Deliberately checks {@code id} as given, not resolved - the
     * raw captured id is exactly what a fresh, not-yet-registered instance needs checked against.
     */
    public boolean isDuplicate(ServerLevel level, UUID id, BlockPos atPos) {
        BlockPos known = positions.get(id);
        if (known == null || known.equals(atPos)) {
            return false;
        }
        return level.hasChunk(known.getX() >> 4, known.getZ() >> 4) && level.getBlockEntity(known) != null;
    }

    /**
     * Records that {@code oldId} was just superseded by {@code newId} - called once, right after
     * {@link #isDuplicate} confirms a collision and the colliding instance mints itself a fresh id.
     * Any existing receivers still registered under {@code oldId} (see {@link #registerReceiver})
     * move over to {@code newId} immediately; anything that only references {@code oldId} from its
     * own saved NBT (a linked reader's own id set, a bound sensor/source field, ...) picks up the
     * new mapping the next time it's actually looked up, via {@link #resolve} - see {@link #supersededBy}'s
     * own doc for why that's enough without rewriting those references directly.
     */
    public void recordSuperseded(UUID oldId, UUID newId) {
        supersededBy.put(oldId, newId);
        Set<ReceiverBlockEntity> receivers = receiversBySource.remove(oldId);
        if (receivers != null && !receivers.isEmpty()) {
            receiversBySource.computeIfAbsent(newId, k -> new HashSet<>()).addAll(receivers);
        }
        setDirty();
    }

    /** 0 for an id that was never registered, was removed, or has never reported a value yet. */
    public int getSignal(UUID id) {
        return signals.getOrDefault(resolve(id), 0);
    }

    /**
     * Called from a receiver's own {@code onLoad}/{@code setBoundSource} - every reload and every
     * (re)bind re-registers (self-healing, same reasoning as {@link #register}). Resolves
     * {@code sourceId} first, so it doesn't matter whether this runs before or after the bound
     * source's own {@link #recordSuperseded} call - either order lands in the same bucket.
     */
    public void registerReceiver(UUID sourceId, ReceiverBlockEntity receiver) {
        receiversBySource.computeIfAbsent(resolve(sourceId), k -> new HashSet<>()).add(receiver);
    }

    /** Called from a receiver's own {@code setBoundSource} (rebinding away from {@code sourceId}) or {@code onRemove}. */
    public void unregisterReceiver(UUID sourceId, ReceiverBlockEntity receiver) {
        UUID resolved = resolve(sourceId);
        Set<ReceiverBlockEntity> receivers = receiversBySource.get(resolved);
        if (receivers == null) {
            return;
        }
        receivers.remove(receiver);
        if (receivers.isEmpty()) {
            receiversBySource.remove(resolved);
        }
    }

    /**
     * Skips (rather than force-loads) a receiver whose own chunk isn't currently loaded - it isn't
     * ticking right now regardless, and will simply read the already-updated {@link #signals} entry
     * itself the moment its chunk loads and its own tick resumes, same as it always has.
     */
    private void notifyReceivers(UUID sourceId, ServerLevel level) {
        Set<ReceiverBlockEntity> receivers = receiversBySource.get(sourceId);
        if (receivers == null || receivers.isEmpty()) {
            return;
        }
        for (ReceiverBlockEntity receiver : Set.copyOf(receivers)) {
            BlockPos pos = receiver.getBlockPos();
            if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                receiver.onSourceSignalChanged(level);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, BlockPos> entry : positions.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("Id", entry.getKey());
            entryTag.put("Pos", NbtUtils.writeBlockPos(entry.getValue()));
            Integer signal = signals.get(entry.getKey());
            if (signal != null && signal != 0) {
                entryTag.putInt("Signal", signal);
            }
            entries.add(entryTag);
        }
        tag.put("Devices", entries);
        ListTag superseded = new ListTag();
        for (Map.Entry<UUID, UUID> entry : supersededBy.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("Old", entry.getKey());
            entryTag.putUUID("New", entry.getValue());
            superseded.add(entryTag);
        }
        tag.put("Superseded", superseded);
        return tag;
    }

    private static DeviceIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        DeviceIndex index = new DeviceIndex();
        for (Tag entry : tag.getList("Devices", Tag.TAG_COMPOUND)) {
            if (!(entry instanceof CompoundTag entryTag) || !entryTag.hasUUID("Id")) {
                continue;
            }
            UUID id = entryTag.getUUID("Id");
            NbtUtils.readBlockPos(entryTag, "Pos").ifPresent(pos -> index.positions.put(id, pos));
            if (entryTag.contains("Signal")) {
                index.signals.put(id, entryTag.getInt("Signal"));
            }
        }
        for (Tag entry : tag.getList("Superseded", Tag.TAG_COMPOUND)) {
            if (entry instanceof CompoundTag entryTag && entryTag.hasUUID("Old") && entryTag.hasUUID("New")) {
                index.supersededBy.put(entryTag.getUUID("Old"), entryTag.getUUID("New"));
            }
        }
        return index;
    }
}
