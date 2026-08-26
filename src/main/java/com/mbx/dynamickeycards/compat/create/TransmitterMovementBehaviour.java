package com.mbx.dynamickeycards.compat.create;

import com.mbx.dynamickeycards.block.DeviceIndex;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Keeps a transmitter's wireless identity alive while it rides a Create contraption: every tick,
 * re-registers its current world position (so {@link DeviceIndex} lookups stay roughly current as
 * it moves) and republishes its signal strength, driven only by a manual trigger armed through
 * {@link TransmitterMovingInteraction} (see {@link #TAG_MANUAL_UNTIL_GAME_TIME}) - no physical
 * wire reading happens here. A contraption has nowhere for a wired redstone signal to come from
 * (see {@code ROADMAP.md}'s "콘트랩션/타 모드 이동 중 사용" section), so a transmitter in
 * {@code REDSTONE_ONLY} mode is simply silent while moving, exactly as it would be sitting on an
 * unpowered wire.
 */
public class TransmitterMovementBehaviour implements MovementBehaviour {

    /** Scratch key in {@link MovementContext#data} - not part of the block's own saved NBT. */
    static final String TAG_MANUAL_UNTIL_GAME_TIME = "ManualUntilGameTime";

    @Override
    public void tick(MovementContext context) {
        if (!(context.world instanceof ServerLevel serverLevel)
                || context.blockEntityData == null || !context.blockEntityData.hasUUID("DeviceId")) {
            return;
        }
        UUID deviceId = context.blockEntityData.getUUID("DeviceId");
        DeviceIndex index = DeviceIndex.get(serverLevel);
        index.register(deviceId, BlockPos.containing(context.position));

        long manualUntil = context.data.contains(TAG_MANUAL_UNTIL_GAME_TIME)
                ? context.data.getLong(TAG_MANUAL_UNTIL_GAME_TIME) : -1;
        int strength = serverLevel.getGameTime() < manualUntil ? 15 : 0;
        index.updateSignal(deviceId, strength, serverLevel);
    }
}
