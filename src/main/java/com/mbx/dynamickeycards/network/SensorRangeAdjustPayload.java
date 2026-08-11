package com.mbx.dynamickeycards.network;

import com.mbx.dynamickeycards.DynamicKeycards;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server: "Ctrl+Scroll while looking at {@code pos}, {@code grow} it or shrink it."
 * Deliberately doesn't say which face - the server re-resolves that itself from the sending
 * player's own look direction (see {@code SensorRangeInputHandler}'s client side, which does
 * the same resolution just to decide whether to send this at all and to cancel the scroll
 * event), rather than trusting a client-supplied direction. This mod's first custom packet -
 * everything else so far rides on vanilla block-interaction packets or block-entity sync,
 * neither of which cover a raw mouse-scroll input.
 */
public record SensorRangeAdjustPayload(BlockPos pos, boolean grow) implements CustomPacketPayload {

    public static final Type<SensorRangeAdjustPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "sensor_range_adjust"));

    public static final StreamCodec<ByteBuf, SensorRangeAdjustPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SensorRangeAdjustPayload::pos,
            ByteBufCodecs.BOOL, SensorRangeAdjustPayload::grow,
            SensorRangeAdjustPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
