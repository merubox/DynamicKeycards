package com.mbx.dynamickeycards.block;

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
    private boolean registerMode;

    public CardReaderBlockEntity(BlockPos pos, BlockState state) {
        super(DKBlockEntities.CARD_READER.get(), pos, state);
    }

    public boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUUID());
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
        this.syncToClient();
    }

    public boolean isRegistered(UUID cardId) {
        return registeredCards.contains(cardId);
    }

    public void registerCard(UUID cardId) {
        registeredCards.add(cardId);
        this.syncToClient();
    }

    public void removeCard(UUID cardId) {
        registeredCards.remove(cardId);
        this.syncToClient();
    }

    public void clearCards() {
        registeredCards.clear();
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
        tag.putBoolean("RegisterMode", registerMode);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        registeredCards.clear();
        for (Tag card : tag.getList("Cards", Tag.TAG_INT_ARRAY)) {
            registeredCards.add(NbtUtils.loadUUID(card));
        }
        registerMode = tag.getBoolean("RegisterMode");
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
