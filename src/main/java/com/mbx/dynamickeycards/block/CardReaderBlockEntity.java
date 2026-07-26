package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DKConfig;
import com.mbx.dynamickeycards.compat.create.CreateLinkCompat;
import com.mbx.dynamickeycards.registry.DKBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * State for a card reader: the owner it bound to when placed, the set of registered
 * keycard ids, and whether register mode is active. Fully synced to clients (update tag +
 * data packet) so both sides resolve interactions the same way.
 */
public class CardReaderBlockEntity extends BlockEntity {

    @Nullable
    private UUID owner;
    private final Set<UUID> registeredCards = new HashSet<>();
    /** Own keys of individually blocked cards — the block always beats the allow list. */
    private final Set<UUID> blockedCards = new HashSet<>();
    private boolean registerMode;
    private boolean resetPending;
    /**
     * Per-reader accept-pulse override in ticks; {@code -1} means "use the config
     * default". No UI writes this yet — it is the storage groundwork for the planned
     * golden/estate-keycard settings screen.
     */
    private int pulseLength = -1;

    /**
     * Create Redstone Link broadcast mode (only meaningful when Create is installed): the two
     * ghost frequency slots, and the opaque {@link CreateLinkCompat} adapter registered against
     * Create's network while this reader is loaded. Both stay empty/{@code null} without Create.
     */
    private final ItemStack[] frequencySlots = {ItemStack.EMPTY, ItemStack.EMPTY};
    /** Normal mode (false) vs broadcast mode (true) — set from the wrench UI's two mode buttons. */
    private boolean broadcastEnabled;
    @Nullable
    private Object createLinkAdapter;

    public CardReaderBlockEntity(BlockPos pos, BlockState state) {
        super(DKBlockEntities.CARD_READER.get(), pos, state);
    }

    public boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        this.syncToClient();
    }

    public boolean isRegisterMode() {
        return registerMode;
    }

    public void setRegisterMode(boolean registerMode) {
        this.registerMode = registerMode;
        this.resetPending = false;
        this.syncToClient();
    }

    /**
     * Whether a golden-keycard full reset is awaiting its confirming second click.
     * Deliberately transient (not saved): any register-mode change clears it, and a
     * chunk reload safely cancels a stale confirmation.
     */
    public boolean isResetPending() {
        return resetPending;
    }

    public void setResetPending(boolean resetPending) {
        this.resetPending = resetPending;
    }

    /** The accept-pulse length in ticks: this reader's override, else the config default. */
    public int getPulseLength() {
        return pulseLength >= 0 ? pulseLength : DKConfig.DEFAULT_PULSE_LENGTH_TICKS.get();
    }

    public boolean hasPulseOverride() {
        return pulseLength >= 0;
    }

    public void setPulseLength(int ticks) {
        this.pulseLength = ticks;
        this.syncToClient();
    }

    /** Back to the config default. */
    public void clearPulseLength() {
        this.pulseLength = -1;
        this.syncToClient();
    }

    /** Ghost frequency slot {@code index} (0 or 1) for Create's Redstone Link broadcast. */
    public ItemStack getFrequencySlot(int index) {
        return frequencySlots[index];
    }

    /**
     * Sets ghost frequency slot {@code index}; the stack is never actually consumed by this
     * (see {@code menu.BroadcastModeMenu}), only remembered as a count-1 copy — stack count
     * doesn't matter for frequency matching.
     */
    public void setFrequencySlot(int index, ItemStack stack) {
        frequencySlots[index] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        reregisterBroadcast();
        this.syncToClient();
    }

    public boolean isBroadcastEnabled() {
        return broadcastEnabled;
    }

    /** Toggled by the wrench UI's normal-mode/broadcast-mode buttons. */
    public void setBroadcastEnabled(boolean broadcastEnabled) {
        this.broadcastEnabled = broadcastEnabled;
        if (broadcastEnabled) {
            registerBroadcast();
        } else {
            unregisterBroadcast();
        }
        this.syncToClient();
    }

    /** Re-announces this reader to Create's Redstone Link network under its current frequency. */
    private void reregisterBroadcast() {
        if (createLinkAdapter != null && level != null) {
            CreateLinkCompat.unregister(level, createLinkAdapter);
            CreateLinkCompat.register(level, createLinkAdapter);
        }
    }

    private void registerBroadcast() {
        if (level == null || level.isClientSide || !CreateLinkCompat.isLoaded()) {
            return;
        }
        if (createLinkAdapter == null) {
            createLinkAdapter = CreateLinkCompat.createAdapter(this);
        }
        CreateLinkCompat.register(level, createLinkAdapter);
    }

    private void unregisterBroadcast() {
        if (createLinkAdapter != null && level != null) {
            CreateLinkCompat.unregister(level, createLinkAdapter);
        }
    }

    /**
     * Tells Create's Redstone Link network to re-poll this reader's transmitted strength.
     * Called by {@link CardReaderBlock} right after the accept pulse starts and right after it
     * ends, so the broadcast strength (15 while accepted, else 0) tracks the physical pulse.
     * A no-op unless Create is installed and this reader is currently registered.
     */
    public void notifyBroadcastChanged() {
        if (createLinkAdapter != null && level != null) {
            CreateLinkCompat.notifyChanged(level, createLinkAdapter);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (broadcastEnabled) {
            registerBroadcast();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        unregisterBroadcast();
        createLinkAdapter = null;
    }

    public boolean isRegistered(UUID cardId) {
        return registeredCards.contains(cardId);
    }

    public void registerCard(UUID cardId) {
        registeredCards.add(cardId);
        this.syncToClient();
    }

    /** True if any of the given keys is registered. Blocking is checked separately, first. */
    public boolean isRegisteredAny(Iterable<UUID> keys) {
        for (UUID key : keys) {
            if (registeredCards.contains(key)) {
                return true;
            }
        }
        return false;
    }

    public int getRegisteredCount() {
        return registeredCards.size();
    }

    public void removeCard(UUID cardId) {
        registeredCards.remove(cardId);
        this.syncToClient();
    }

    public void clearCards() {
        registeredCards.clear();
        blockedCards.clear();
        this.syncToClient();
    }

    public boolean isBlocked(UUID ownKey) {
        return blockedCards.contains(ownKey);
    }

    public void blockCard(UUID ownKey) {
        blockedCards.add(ownKey);
        this.syncToClient();
    }

    public void unblockCard(UUID ownKey) {
        blockedCards.remove(ownKey);
        this.syncToClient();
    }

    private void syncToClient() {
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
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
        tag.putBoolean("RegisterMode", registerMode);
        if (pulseLength >= 0) {
            tag.putInt("PulseLength", pulseLength);
        }
        if (!frequencySlots[0].isEmpty()) {
            tag.put("FrequencySlot0", frequencySlots[0].save(registries));
        }
        if (!frequencySlots[1].isEmpty()) {
            tag.put("FrequencySlot1", frequencySlots[1].save(registries));
        }
        tag.putBoolean("BroadcastEnabled", broadcastEnabled);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        registeredCards.clear();
        for (Tag card : tag.getList("Cards", Tag.TAG_INT_ARRAY)) {
            registeredCards.add(NbtUtils.loadUUID(card));
        }
        blockedCards.clear();
        for (Tag card : tag.getList("Blocked", Tag.TAG_INT_ARRAY)) {
            blockedCards.add(NbtUtils.loadUUID(card));
        }
        registerMode = tag.getBoolean("RegisterMode");
        pulseLength = tag.contains("PulseLength") ? tag.getInt("PulseLength") : -1;
        frequencySlots[0] = tag.contains("FrequencySlot0")
                ? ItemStack.parseOptional(registries, tag.getCompound("FrequencySlot0")) : ItemStack.EMPTY;
        frequencySlots[1] = tag.contains("FrequencySlot1")
                ? ItemStack.parseOptional(registries, tag.getCompound("FrequencySlot1")) : ItemStack.EMPTY;
        broadcastEnabled = tag.getBoolean("BroadcastEnabled");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
