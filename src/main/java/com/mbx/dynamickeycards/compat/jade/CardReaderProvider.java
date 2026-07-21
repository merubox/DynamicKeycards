package com.mbx.dynamickeycards.compat.jade;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.block.CardReaderBlockEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.GameProfileCache;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.UUID;

/**
 * Reader tooltip: "Owner: name" (name resolved server-side so offline owners still show)
 * and a "register mode armed" line while registration is open. Registered-card contents
 * are deliberately not listed — cards, not the reader, are the visible interface.
 */
public enum CardReaderProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "card_reader");
    private static final String OWNER_NAME_KEY = "DKOwnerName";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof CardReaderBlockEntity reader) {
            UUID owner = reader.getOwner();
            if (owner != null) {
                MinecraftServer server = accessor.getLevel().getServer();
                GameProfileCache cache = server != null ? server.getProfileCache() : null;
                String name = cache != null
                        ? cache.get(owner).map(GameProfile::getName).orElse("")
                        : "";
                data.putString(OWNER_NAME_KEY, name);
            }
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof CardReaderBlockEntity reader)) {
            return;
        }
        if (accessor.getServerData().contains(OWNER_NAME_KEY)) {
            String name = accessor.getServerData().getString(OWNER_NAME_KEY);
            Component owner = name.isEmpty()
                    ? Component.translatable("dynamickeycards.jade.unknown")
                    : Component.literal(name);
            tooltip.add(Component.translatable("dynamickeycards.jade.owner", owner));
        }
        if (reader.isRegisterMode()) {
            tooltip.add(Component.translatable("dynamickeycards.jade.register_mode"));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
