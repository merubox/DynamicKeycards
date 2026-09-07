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
public class CardDuplicatorBlockEntity extends BlockEntity implements WrenchPickupTarget {


    @Nullable
    private List<UUID> sourceKeys;
    private boolean sourceIsManager;
    private final WrenchPickupState wrenchPickup = new WrenchPickupState();

    public CardDuplicatorBlockEntity(BlockPos pos, BlockState state) {
        super(DKBlockEntities.CARD_DUPLICATOR.get(), pos, state);
    }

    @Override
    public boolean isWrenchPickupPending() {
        return wrenchPickup.isPending(level);
    }

    @Override
    public void armWrenchPickupPending() {
        wrenchPickup.arm(level);
    }

    @Override
    public void clearPendingActions() {
        wrenchPickup.clear();
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

    /**
     * Note {@code SourceIsManager} is written unconditionally, even though it only means anything
     * alongside {@code SourceKeys}: it keeps the tag from ever being empty. A block entity update
     * packet carrying an empty tag is discarded client-side without loading it
     * ({@code ClientPacketListener#handleBlockEntityData} skips {@code loadWithComponents} for an
     * empty tag), so clearing the source used to leave clients still holding the old keys - the
     * Jade tooltip kept reading "copy pending" after a cancel.
     */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("SourceIsManager", sourceIsManager);
        if (sourceKeys != null) {
            ListTag keys = new ListTag();
            for (UUID key : sourceKeys) {
                keys.add(NbtUtils.createUUID(key));
            }
            tag.put("SourceKeys", keys);
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
