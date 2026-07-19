package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.registry.DKBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Holds the key of the source card while a duplication is in progress. Synced to
 * clients so both sides resolve interactions the same way.
 */
public class CardDuplicatorBlockEntity extends BlockEntity {

    @Nullable
    private UUID sourceKey;

    public CardDuplicatorBlockEntity(BlockPos pos, BlockState state) {
        super(DKBlockEntities.CARD_DUPLICATOR.get(), pos, state);
    }

    @Nullable
    public UUID getSourceKey() {
        return sourceKey;
    }

    public void setSourceKey(@Nullable UUID sourceKey) {
        this.sourceKey = sourceKey;
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (sourceKey != null) {
            tag.putUUID("SourceKey", sourceKey);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sourceKey = tag.hasUUID("SourceKey") ? tag.getUUID("SourceKey") : null;
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
