package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.item.EstateKeycardItem;
import com.mbx.dynamickeycards.item.GoldenKeycardItem;
import com.mbx.dynamickeycards.item.KeycardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Which cards a {@code CardReaderBlockEntity} lets through, and which other readers it shares that
 * answer with - the allow list, the block list, the linked-reader ids, their save format, and the
 * accept rule itself. Factored out of the block entity, which had grown seven unrelated concerns
 * (ownership, register mode, signal length, frequency, device id, wireless publishing, and this)
 * in one class.
 *
 * <p>Owned by (not extended from) the block entity, the same arrangement {@link LinkDeviceState}
 * uses and for the same reason - and, like that class, it deliberately does not call the owner's
 * {@code syncToClient()}: every mutating method here leaves that to the caller, since only the
 * block entity knows its own sync mechanics. Each of the block entity's methods still syncs
 * exactly when it always did.
 */
final class CardAccessState {

    /** The reader whose access this is - needed to walk out to its linked group, see {@link #linkedGroup}. */
    private final CardReaderBlockEntity reader;
    private final Set<UUID> registeredCards = new HashSet<>();
    /** Own keys of individually blocked cards — the block always beats the allow list. */
    private final Set<UUID> blockedCards = new HashSet<>();
    /**
     * Other readers this one shares registered/blocked cards with (see {@link #accepts}) - each entry added on both ends when one reader is
     * placed while linked to the other (see {@code LinkedReaderBlockItem}/
     * {@code CardReaderBlock#setPlacedBy}). Everything else about a reader - owner, mode/frequency,
     * pulse - stays independent; this only affects which cards a tap there accepts. Identifies each
     * linked reader by its device id rather than a raw {@link BlockPos} - see {@link DeviceIndex}'s
     * own doc for why.
     *
     * <p>A set, not a single link: the whole connected group - however many readers, in whatever
     * shape (chain, star, even a loop) - shares one combined accept list, so {@link #accepts} walks
     * the full reachable set (see {@link #linkedGroup}), not just this field's own direct entries.
     */
    private final Set<UUID> linkedReaderIds = new HashSet<>();
    /**
     * Legacy {@link BlockPos}-based links read from a pre-0.1.8 save, held here only until
     * {@link #resolveLegacyLinks} can resolve each one to the linked reader's own device id (which
     * requires the level, not available yet during {@link #load}).
     */
    private List<BlockPos> legacyLinkedReaderPositions = List.of();

    CardAccessState(CardReaderBlockEntity reader) {
        this.reader = reader;
    }

    // ---- Registrations ----

    boolean isRegistered(UUID cardId) {
        return registeredCards.contains(cardId);
    }

    /** True if any of the given keys is registered. Blocking is checked separately, first. */
    boolean isRegisteredAny(Iterable<UUID> keys) {
        for (UUID key : keys) {
            if (registeredCards.contains(key)) {
                return true;
            }
        }
        return false;
    }

    int registeredCount() {
        return registeredCards.size();
    }

    void registerCard(UUID cardId) {
        registeredCards.add(cardId);
    }

    void removeCard(UUID cardId) {
        registeredCards.remove(cardId);
    }

    /** Both lists at once - what a full reset clears. */
    void clear() {
        registeredCards.clear();
        blockedCards.clear();
    }

    // ---- Blocks ----

    boolean isBlocked(UUID ownKey) {
        return blockedCards.contains(ownKey);
    }

    void blockCard(UUID ownKey) {
        blockedCards.add(ownKey);
    }

    void unblockCard(UUID ownKey) {
        blockedCards.remove(ownKey);
    }

    // ---- Linked readers ----

    /** A copy, for anything outside this reader - see {@link #linkedIds} for {@link #linkedGroup}'s own view. */
    Set<UUID> linkedReaders() {
        return Set.copyOf(linkedReaderIds);
    }

    /**
     * The live set, for {@link #linkedGroup} only - it reaches into every linked reader's own state
     * in turn and would otherwise copy once per node.
     * Iterate, don't mutate.
     */
    Set<UUID> linkedIds() {
        return linkedReaderIds;
    }

    /** @return whether anything actually changed, so the caller knows whether to sync. */
    boolean addLinkedReader(UUID id) {
        return linkedReaderIds.add(id);
    }

    /** @return whether anything actually changed, so the caller knows whether to sync. */
    boolean removeLinkedReader(UUID id) {
        return linkedReaderIds.remove(id);
    }

    /**
     * Best-effort migration for a pre-0.1.8 save's {@link BlockPos}-based links (see
     * {@link #legacyLinkedReaderPositions}): resolves each stored position to the reader actually
     * there right now and adopts its device id. Only works for a target whose chunk happens to be
     * loaded at this exact moment - one whose chunk is still unloaded is simply dropped rather than
     * force-loaded (out of scope for this pass, see {@code ROADMAP.md}), so a link to a very
     * distant reader may need to be re-established by hand after upgrading. Runs once -
     * {@link #legacyLinkedReaderPositions} is cleared after, regardless of how many resolved, so a
     * reload doesn't keep re-attempting positions that were already given up on.
     *
     * @return whether anything actually changed, so the caller knows whether to sync.
     */
    boolean resolveLegacyLinks(Level level) {
        if (legacyLinkedReaderPositions.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (BlockPos pos : legacyLinkedReaderPositions) {
            if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                    && level.getBlockEntity(pos) instanceof CardReaderBlockEntity linked) {
                linkedReaderIds.add(linked.getDeviceId());
                changed = true;
            }
        }
        legacyLinkedReaderPositions = List.of();
        return changed;
    }

    // ---- The linked group ----

    /**
     * This reader plus every reader reachable by following {@link #linkedReaderIds} from here - a
     * breadth-first walk of the whole connected group, however many readers are in it or whatever
     * shape they're linked in (chain, star, even a loop; a loop is exactly as safe as any other
     * shape since {@code visited} stops it from being walked twice). Two readers linked only
     * through a third one still end up sharing one accept list this way, which a single
     * direct-neighbor check wouldn't give them.
     *
     * <p>Resolving an id to a position (via {@link DeviceIndex}) and then reading its actual data
     * still needs {@code Level#getBlockEntity}, which force-loads whatever chunk that position is
     * in if it isn't already loaded (confirmed against vanilla's own {@code LevelReader}) - the id
     * migration fixes staleness and Create-move safety, not this cost, see
     * {@code CardReaderBlockEntity}'s class doc. Callers on a hot path (see {@link #accepts} /
     * {@link #acceptsAny}) should call this once and reuse the result rather than once per
     * item/check.
     */
    private Set<CardReaderBlockEntity> linkedGroup() {
        Set<CardReaderBlockEntity> visited = new HashSet<>();
        visited.add(reader);
        Level level = reader.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return visited;
        }
        DeviceIndex index = DeviceIndex.get(serverLevel);
        Deque<CardReaderBlockEntity> frontier = new ArrayDeque<>(visited);
        while (!frontier.isEmpty()) {
            CardReaderBlockEntity current = frontier.poll();
            for (UUID id : current.cardAccess().linkedIds()) {
                BlockPos pos = index.getPosition(id);
                if (pos != null && level.getBlockEntity(pos) instanceof CardReaderBlockEntity linked && visited.add(linked)) {
                    frontier.add(linked);
                }
            }
        }
        return visited;
    }

    /**
     * Whether {@code key} is registered anywhere in this reader's linked group (see
     * {@link #linkedGroup}), not just here - what {@code CardReaderBlock#useItemOn}'s register-mode
     * tap should check before deciding to register vs. remove, so tapping a card that's already
     * registered via a linked reader is recognized as "already has access" instead of silently
     * registering a second, redundant local copy here too.
     */
    boolean isRegisteredInGroup(UUID key) {
        return isRegisteredAnyIn(List.of(key), linkedGroup());
    }

    /** Same as {@link #isRegisteredInGroup}, for {@link #isBlocked}. */
    boolean isBlockedInGroup(UUID key) {
        return isBlockedIn(key, linkedGroup());
    }

    /** Same as {@link #isRegisteredInGroup}, for {@link #isRegisteredAny}. */
    boolean isRegisteredAnyInGroup(Iterable<UUID> keys) {
        return isRegisteredAnyIn(keys, linkedGroup());
    }

    /**
     * Removes {@code key} from wherever it's actually stored across this reader's linked group -
     * not just here. {@link #removeCard} alone would leave a stale registration on whichever
     * reader the card was originally registered on, so revoking from a different reader in the
     * same group would silently not take.
     *
     * <p>The three group mutators below go through each reader's own block entity rather than
     * straight to its {@code CardAccessState}: only the block entity syncs the result to the
     * client, and a reader whose list changed without syncing would keep showing the old one.
     * The read-only walks above have no such need and reach into the state directly.
     */
    void removeRegisteredFromGroup(UUID key) {
        for (CardReaderBlockEntity linked : linkedGroup()) {
            linked.removeCard(key);
        }
    }

    /** Same as {@link #removeRegisteredFromGroup}, for {@link #unblockCard}. */
    void unblockInGroup(UUID key) {
        for (CardReaderBlockEntity linked : linkedGroup()) {
            linked.unblockCard(key);
        }
    }

    /**
     * Same as {@link #removeRegisteredFromGroup}, for {@link #clear} - and for the same reason. A
     * group shares one combined accept list, so wiping only this reader's own two lists leaves
     * every card registered on a linked reader still passing here: the reset visibly doesn't take.
     * Wipes the whole group, exactly as revoking a single card already did.
     */
    void clearCardsInGroup() {
        for (CardReaderBlockEntity linked : linkedGroup()) {
            linked.clearCards();
        }
    }

    // ---- The accept rule ----

    /**
     * Whether tapping {@code stack} against this reader right now would be accepted - the same
     * rule {@code CardReaderBlock#useItemOn}'s tap path applies, minus the side effects. Register
     * mode isn't considered here - that's a physical-tap-only flow (arming/toggling registration),
     * not something an ambient sensor should ever trigger.
     */
    boolean accepts(ItemStack stack) {
        return accepts(stack, linkedGroup());
    }

    /**
     * Whether any non-empty stack in {@code inventory} would be accepted by this reader right
     * now - same rule as {@link #accepts}, but for {@code AdvancedSensorBlockEntity}'s per-tick
     * whole-inventory scan: {@link #linkedGroup} (a BFS that can force-load every linked reader's
     * chunk, see its own doc) is computed once for the whole inventory here, instead of once per
     * stack the way calling {@link #accepts} in a loop would.
     */
    boolean acceptsAny(Inventory inventory) {
        Set<CardReaderBlockEntity> group = linkedGroup();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && accepts(stack, group)) {
                return true;
            }
        }
        return false;
    }

    private boolean accepts(ItemStack stack, Set<CardReaderBlockEntity> group) {
        if (stack.getItem() instanceof GoldenKeycardItem) {
            return true;
        }
        if (stack.getItem() instanceof EstateKeycardItem) {
            UUID cardOwner = EstateKeycardItem.boundOwner(stack);
            return cardOwner != null && cardOwner.equals(reader.getOwner());
        }
        if (!(stack.getItem() instanceof KeycardItem)) {
            return false;
        }
        UUID ownKey = KeycardItem.ownKey(stack);
        if (ownKey == null || isBlockedIn(ownKey, group)) {
            return false;
        }
        return isRegisteredAnyIn(KeycardItem.allKeys(stack), group);
    }

    /** {@link #isBlocked}, but honoring every reader in {@code group} - see {@link #linkedGroup}. */
    private static boolean isBlockedIn(UUID ownKey, Set<CardReaderBlockEntity> group) {
        for (CardReaderBlockEntity linked : group) {
            if (linked.cardAccess().isBlocked(ownKey)) {
                return true;
            }
        }
        return false;
    }

    /** {@link #isRegisteredAny}, but honoring every reader in {@code group} - see {@link #linkedGroup}. */
    private static boolean isRegisteredAnyIn(Iterable<UUID> keys, Set<CardReaderBlockEntity> group) {
        for (CardReaderBlockEntity linked : group) {
            if (linked.cardAccess().isRegisteredAny(keys)) {
                return true;
            }
        }
        return false;
    }

    // ---- Persistence ----

    /**
     * Key names are load-bearing beyond this class: {@code CardReaderMovingInteraction} reads
     * {@code Cards} and {@code Blocked} straight out of a contraption's captured NBT, with no
     * block entity to ask.
     */
    void save(CompoundTag tag) {
        ListTag cards = new ListTag();
        for (UUID id : registeredCards) {
            cards.add(NbtUtils.createUUID(id));
        }
        tag.put("Cards", cards);
        ListTag blocked = new ListTag();
        for (UUID id : blockedCards) {
            blocked.add(NbtUtils.createUUID(id));
        }
        tag.put("Blocked", blocked);
        ListTag linked = new ListTag();
        for (UUID id : linkedReaderIds) {
            linked.add(NbtUtils.createUUID(id));
        }
        tag.put("LinkedReaderIds", linked);
    }

    void load(CompoundTag tag) {
        registeredCards.clear();
        for (Tag card : tag.getList("Cards", Tag.TAG_INT_ARRAY)) {
            registeredCards.add(NbtUtils.loadUUID(card));
        }
        blockedCards.clear();
        for (Tag card : tag.getList("Blocked", Tag.TAG_INT_ARRAY)) {
            blockedCards.add(NbtUtils.loadUUID(card));
        }
        linkedReaderIds.clear();
        if (tag.contains("LinkedReaderIds")) {
            // 0.1.8+ save: already device ids, no resolution needed
            for (Tag entry : tag.getList("LinkedReaderIds", Tag.TAG_INT_ARRAY)) {
                linkedReaderIds.add(NbtUtils.loadUUID(entry));
            }
            return;
        }
        // pre-0.1.8 save: BlockPos-based, resolved to ids best-effort once the level is
        // available - see #resolveLegacyLinks
        List<BlockPos> legacy = new ArrayList<>();
        // pre-0.1.6 saves only ever had one entry, under the old singular key
        if (tag.contains("LinkedReader")) {
            NbtUtils.readBlockPos(tag, "LinkedReader").ifPresent(legacy::add);
        }
        for (Tag entry : tag.getList("LinkedReaders", Tag.TAG_INT_ARRAY)) {
            if (entry instanceof IntArrayTag intArray && intArray.getAsIntArray().length == 3) {
                int[] xyz = intArray.getAsIntArray();
                legacy.add(new BlockPos(xyz[0], xyz[1], xyz[2]));
            }
        }
        legacyLinkedReaderPositions = legacy;
    }
}
