package com.mbx.dynamickeycards.compat.jade;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.block.AdvancedSensorBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Advanced sensor tooltip: whether it's bound to a reader or to another advanced sensor - both
 * fields are already synced to the client as part of this block entity's own normal update tag
 * (see {@code AdvancedSensorBlockEntity#saveAdditional}), so unlike {@link CardReaderProvider}'s
 * owner name or {@link ReceiverProvider}'s source kind, no server round-trip is needed here.
 * A sensor-to-sensor binding is always mutual by construction (see
 * {@code AdvancedSensorBlockEntity#applyPlacedBinding}), hence the distinct "mutually connected"
 * wording rather than reusing the reader case's one-directional phrasing.
 */
public enum AdvancedSensorProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "advanced_sensor");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof AdvancedSensorBlockEntity sensor)) {
            return;
        }
        if (sensor.getBoundReader() != null) {
            tooltip.add(Component.translatable("dynamickeycards.jade.connected_to.reader"));
        } else if (sensor.getBoundSensor() != null) {
            tooltip.add(Component.translatable("dynamickeycards.jade.connected_to.mutual"));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
