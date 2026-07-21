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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Holds the frozen key set of the source card while a duplication is in progress —
 * the keys the source and the copy will share. Synced to clients so both sides resolve
 * interactions the same way.
 */
public class CardDuplicatorBlockEntity extends BlockEntity {

    @Nullable
    private List<UUID> sourceKeys;
    private boolean sourceIsManager;

    public CardDuplicatorBlockEntity(BlockPos pos, BlockState state) {
        super(DKBlockEntities.CARD_DUPLICATOR.get(), pos, state);
    }

    @Nullable
    public List<UUID> getSourceKeys() {
        return sourceKeys;
    }

    public boolean isSourceManager() {
        return sourceIsManager;
    }

    public void setSourceKeys(@Nullable List<UUID> sourceKeys) {
        setSourceKeys(sourceKeys, false);
    }

    public void setSourceKeys(@Nullable List<UUID> sourceKeys, boolean manager) {
        this.sourceKeys = sourceKeys == null ? null : List.copyOf(sourceKeys);
        this.sourceIsManager = sourceKeys != null && manager;
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (sourceKeys != null) {
            ListTag keys = new ListTag();
            for (UUID key : sourceKeys) {
                keys.add(NbtUtils.createUUID(key));
            }
            tag.put("SourceKeys", keys);
            tag.putBoolean("SourceIsManager", sourceIsManager);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("SourceKeys")) {
            List<UUID> keys = new java.util.ArrayList<>();
            for (Tag key : tag.getList("SourceKeys", Tag.TAG_INT_ARRAY)) {
                keys.add(NbtUtils.loadUUID(key));
            }
            sourceKeys = List.copyOf(keys);
            sourceIsManager = tag.getBoolean("SourceIsManager");
        } else {
            sourceKeys = null;
            sourceIsManager = false;
        }
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
