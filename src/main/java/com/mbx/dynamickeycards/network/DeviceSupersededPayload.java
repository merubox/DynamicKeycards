package com.mbx.dynamickeycards.network;

import com.mbx.dynamickeycards.DynamicKeycards;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Server-to-client: a device (reader, sensor, transmitter, or receiver) just discovered its own
 * stored id already belongs to another instance (see {@code DeviceIndex#recordSuperseded}) and
 * minted itself a fresh one - {@code oldId} now resolves to {@code pos} too, same as it already
 * does server-side through {@code DeviceIndex#resolve}'s own redirect chain.
 *
 * <p>{@code ClientDeviceCache} (the client-only mirror {@code DKClientEvents}' bind-target
 * highlight reads) has no notion of that redirect chain at all - it's a flat id-to-position map,
 * populated purely from each device's own {@code onLoad}, which only ever registers under its
 * <em>current</em> id. An item still holding the old id (bound before the collision happened -
 * most commonly by picking the original block up with something that preserves its saved NBT,
 * carrying it elsewhere, and placing it back down somewhere the id collides with a copy already
 * registered there) would resolve correctly server-side for every real interaction, but the
 * client-only highlight would silently and permanently fail for that exact item, since
 * {@code ClientDeviceCache.getPosition(oldId)} never had anywhere to look. Broadcasting this once,
 * right when the redirect is recorded, is enough to fix it: the client just adds one more entry
 * pointing at the same spot, no redirect-chain logic needed on that side at all.
 */
public record DeviceSupersededPayload(UUID oldId, BlockPos pos) implements CustomPacketPayload {

    public static final Type<DeviceSupersededPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "device_superseded"));

    public static final StreamCodec<ByteBuf, DeviceSupersededPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, DeviceSupersededPayload::oldId,
            BlockPos.STREAM_CODEC, DeviceSupersededPayload::pos,
            DeviceSupersededPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
