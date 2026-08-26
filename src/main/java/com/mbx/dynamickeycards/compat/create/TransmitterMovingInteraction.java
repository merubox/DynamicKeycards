package com.mbx.dynamickeycards.compat.create;

import com.mbx.dynamickeycards.DKSounds;
import com.mbx.dynamickeycards.block.TransmitterMode;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Ports {@code TransmitterBlock}'s standing + empty-hand manual trigger onto a moving contraption -
 * the only interaction a transmitter has that doesn't depend on a physical wire (see
 * {@link TransmitterMovementBehaviour}'s own doc). Sneaking (mode config UI) isn't ported here -
 * that's an admin action, out of scope for a block riding a contraption, same reasoning as the
 * card reader's own {@code CardReaderMovingInteraction}.
 */
public class TransmitterMovingInteraction extends MovingInteractionBehaviour {

    @Override
    public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos localPos,
                                            AbstractContraptionEntity contraptionEntity) {
        if (player.level().isClientSide || player.isShiftKeyDown() || !player.getItemInHand(hand).isEmpty()) {
            return false;
        }
        Pair<?, MovementContext> actor = contraptionEntity.getContraption().getActorAt(localPos);
        if (actor == null) {
            return false;
        }
        MovementContext context = actor.getRight();
        CompoundTag blockEntityData = context.blockEntityData;
        if (blockEntityData == null || !blockEntityData.hasUUID("DeviceId")) {
            return false;
        }
        TransmitterMode mode = TransmitterMode.byName(blockEntityData.getString("Mode"));
        if (mode != TransmitterMode.MIXED) {
            return false;
        }
        int manualTriggerTicks = blockEntityData.contains("ManualTriggerTicks")
                ? blockEntityData.getInt("ManualTriggerTicks") : 20;
        long now = player.level().getGameTime();
        context.data.putLong(TransmitterMovementBehaviour.TAG_MANUAL_UNTIL_GAME_TIME, now + manualTriggerTicks);
        DKSounds.accept(player.level(), BlockPos.containing(context.position));
        return true;
    }
}
