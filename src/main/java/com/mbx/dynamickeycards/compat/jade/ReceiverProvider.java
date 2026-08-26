package com.mbx.dynamickeycards.compat.jade;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.block.AdvancedSensorBlockEntity;
import com.mbx.dynamickeycards.block.CardReaderBlockEntity;
import com.mbx.dynamickeycards.block.DeviceIndex;
import com.mbx.dynamickeycards.block.ReceiverBlockEntity;
import com.mbx.dynamickeycards.block.TransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.UUID;

/**
 * Receiver tooltip: what kind of wireless source it's currently bound to - a reader, a
 * transmitter, or an advanced sensor. Resolved server-side (see {@link #appendServerData}), same
 * reasoning as {@link CardReaderProvider}'s owner name: {@link DeviceIndex#getPosition} needs a
 * {@code ServerLevel}, unavailable from the client-side accessor {@link #appendTooltip} runs
 * with. Silently shows nothing while unbound, or while the source's chunk isn't currently loaded
 * (same "best effort, no forced loading" restraint {@code MaintenanceAccess#resolveReaderOwner}
 * already uses) - there's nothing wrong to report in either case.
 */
public enum ReceiverProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "receiver");
    private static final String SOURCE_KIND_KEY = "DKSourceKind";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof ReceiverBlockEntity receiver)) {
            return;
        }
        UUID sourceId = receiver.getBoundSource();
        if (sourceId == null || !(accessor.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos sourcePos = DeviceIndex.get(serverLevel).getPosition(sourceId);
        if (sourcePos == null || !serverLevel.isLoaded(sourcePos)) {
            return;
        }
        BlockEntity source = serverLevel.getBlockEntity(sourcePos);
        String kind = source instanceof CardReaderBlockEntity ? "reader"
                : source instanceof TransmitterBlockEntity ? "transmitter"
                : source instanceof AdvancedSensorBlockEntity ? "sensor"
                : null;
        if (kind != null) {
            data.putString(SOURCE_KIND_KEY, kind);
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getServerData().contains(SOURCE_KIND_KEY)) {
            return;
        }
        String kind = accessor.getServerData().getString(SOURCE_KIND_KEY);
        tooltip.add(Component.translatable("dynamickeycards.jade.connected_to." + kind));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
