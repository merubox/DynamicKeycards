package com.mbx.dynamickeycards.compat.create;

import com.mbx.dynamickeycards.DKSounds;
import com.mbx.dynamickeycards.item.EstateKeycardItem;
import com.mbx.dynamickeycards.item.GoldenKeycardItem;
import com.mbx.dynamickeycards.item.KeycardItem;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;

import java.util.UUID;

/**
 * Ports a card tap onto a moving contraption - only the "is this card accepted" check (this
 * reader's own registrations, no linked-group traversal - see {@code ROADMAP.md}'s "최소 이식"
 * note) and the resulting wireless publish (via {@link CardReaderMovementBehaviour}). Register
 * mode, admin actions, and physical redstone output are all out of scope here - a reader riding a
 * contraption can only ever be tapped for passage, not administered.
 */
public class CardReaderMovingInteraction extends MovingInteractionBehaviour {

    @Override
    public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos localPos,
                                            AbstractContraptionEntity contraptionEntity) {
        if (player.level().isClientSide) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof KeycardItem)) {
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
        BlockPos worldPos = BlockPos.containing(context.position);
        if (!accepts(stack, blockEntityData)) {
            DKSounds.deny(player.level(), worldPos);
            return true;
        }
        long now = player.level().getGameTime();
        context.data.putLong(CardReaderMovementBehaviour.TAG_MOMENTARY_UNTIL_GAME_TIME,
                now + CardReaderMovementBehaviour.MOMENTARY_TICKS);
        DKSounds.accept(player.level(), worldPos);
        return true;
    }

    /** Single-reader check only - {@code CardReaderBlockEntity#accepts}'s own doc explains the full (group-aware) version this simplifies. */
    private static boolean accepts(ItemStack stack, CompoundTag blockEntityData) {
        if (stack.getItem() instanceof GoldenKeycardItem) {
            return true;
        }
        UUID readerOwner = blockEntityData.hasUUID("Owner") ? blockEntityData.getUUID("Owner") : null;
        if (stack.getItem() instanceof EstateKeycardItem) {
            UUID cardOwner = EstateKeycardItem.boundOwner(stack);
            return cardOwner != null && cardOwner.equals(readerOwner);
        }
        UUID ownKey = KeycardItem.ownKey(stack);
        if (ownKey == null || containsUUID(blockEntityData, "Blocked", ownKey)) {
            return false;
        }
        for (UUID key : KeycardItem.allKeys(stack)) {
            if (containsUUID(blockEntityData, "Cards", key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsUUID(CompoundTag tag, String listKey, UUID id) {
        for (Tag entry : tag.getList(listKey, Tag.TAG_INT_ARRAY)) {
            if (NbtUtils.loadUUID(entry).equals(id)) {
                return true;
            }
        }
        return false;
    }
}
