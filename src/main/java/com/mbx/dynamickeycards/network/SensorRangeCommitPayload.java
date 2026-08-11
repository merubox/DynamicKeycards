package com.mbx.dynamickeycards.network;

import com.mbx.dynamickeycards.DynamicKeycards;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server: right-click (confirm) or left-click (cancel) while the cursor is over an
 * armed sensor's highlight box - see {@code SensorRangeClientHandler#onClickInput}. Same
 * "server re-resolves everything itself" posture as {@link SensorRangeAdjustPayload}: this only
 * says which sensor and which of the two actions, not anything about where the cursor actually
 * was - the server redoes that hit test against the player's own current look ray.
 */
public record SensorRangeCommitPayload(BlockPos pos, boolean confirm) implements CustomPacketPayload {

    public static final Type<SensorRangeCommitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "sensor_range_commit"));

    public static final StreamCodec<ByteBuf, SensorRangeCommitPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SensorRangeCommitPayload::pos,
            ByteBufCodecs.BOOL, SensorRangeCommitPayload::confirm,
            SensorRangeCommitPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
