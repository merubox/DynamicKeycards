package com.mbx.dynamickeycards.compat.create;

import com.mbx.dynamickeycards.block.DeviceIndex;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Keeps a card reader's wireless identity alive while it rides a Create contraption - same shape
 * as {@link TransmitterMovementBehaviour}, see there for why only position + wireless publish are
 * ported (no physical redstone while moving). {@link CardReaderMovingInteraction} arms
 * {@link #TAG_MOMENTARY_UNTIL_GAME_TIME} on an accepted card tap; this decays it back to 0 the
 * same {@code MOMENTARY_TICKS} window {@code CardReaderBlockEntity#getSignalSourceStrength} uses
 * on a stationary reader.
 */
public class CardReaderMovementBehaviour implements MovementBehaviour {

    /** Scratch key in {@link MovementContext#data} - not part of the block's own saved NBT. */
    static final String TAG_MOMENTARY_UNTIL_GAME_TIME = "MomentaryUntilGameTime";
    /** Mirrors {@link com.mbx.dynamickeycards.block.CardReaderBlockEntity#MOMENTARY_TICKS}. */
    static final int MOMENTARY_TICKS = 4;

    @Override
    public void tick(MovementContext context) {
        if (!(context.world instanceof ServerLevel serverLevel)
                || context.blockEntityData == null || !context.blockEntityData.hasUUID("DeviceId")) {
            return;
        }
        UUID deviceId = context.blockEntityData.getUUID("DeviceId");
        DeviceIndex index = DeviceIndex.get(serverLevel);
        index.register(deviceId, BlockPos.containing(context.position));

        long momentaryUntil = context.data.contains(TAG_MOMENTARY_UNTIL_GAME_TIME)
                ? context.data.getLong(TAG_MOMENTARY_UNTIL_GAME_TIME) : -1;
        int strength = serverLevel.getGameTime() < momentaryUntil ? 15 : 0;
        index.updateSignal(deviceId, strength, serverLevel);
    }
}
