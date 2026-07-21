package com.mbx.dynamickeycards.compat.jade;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.block.CardDuplicatorBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Duplicator tooltip: a "copy pending" line while a source card is inserted. The pending
 * source is fully synced to clients, so no server data round-trip is needed.
 */
public enum CardDuplicatorProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "card_duplicator");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getBlockEntity() instanceof CardDuplicatorBlockEntity duplicator
                && duplicator.getSourceKeys() != null) {
            tooltip.add(Component.translatable("dynamickeycards.jade.copy_pending"));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
