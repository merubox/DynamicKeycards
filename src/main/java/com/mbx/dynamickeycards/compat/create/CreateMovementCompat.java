package com.mbx.dynamickeycards.compat.create;

import com.mbx.dynamickeycards.registry.DKBlocks;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.List;

/**
 * One-time registration of this mod's {@link MovementBehaviour}/{@link MovingInteractionBehaviour}
 * instances against Create's own per-{@link Block} registries - see {@link CardReaderMovementBehaviour}/
 * {@link TransmitterMovementBehaviour} for what actually happens on a moving contraption. Called
 * from common setup, gated by {@code CreateAvailability.isLoaded()} same as every other Create
 * touchpoint in this mod.
 *
 * <p>Every card reader variant is its own {@link Block} instance (insert/touch/swipe/advanced/
 * obsidian all share {@code CardReaderBlock} as a Java class, but Create's registries are keyed
 * per-instance, not per-class) - the same behaviour pair is registered against all five. Plain
 * motion sensors, advanced sensors, and the receiver aren't registered here: none of them can do
 * anything useful while frozen mid-contraption (no physical redstone output is possible there,
 * see {@code ROADMAP.md}, and a plain/advanced sensor's own passive entity-detection scan isn't
 * ported yet - left as a follow-up, noted in {@code ROADMAP.md}).
 */
public final class CreateMovementCompat {

    private CreateMovementCompat() {
    }

    public static void register() {
        MovementBehaviour readerMovement = new CardReaderMovementBehaviour();
        MovingInteractionBehaviour readerInteraction = new CardReaderMovingInteraction();
        for (DeferredBlock<Block> reader : List.of(
                DKBlocks.INSERT_CARD_READER, DKBlocks.TOUCH_CARD_READER, DKBlocks.SWIPE_CARD_READER,
                DKBlocks.ADVANCED_CARD_READER, DKBlocks.OBSIDIAN_CARD_READER)) {
            MovementBehaviour.REGISTRY.register(reader.get(), readerMovement);
            MovingInteractionBehaviour.REGISTRY.register(reader.get(), readerInteraction);
        }
        MovementBehaviour.REGISTRY.register(DKBlocks.TRANSMITTER.get(), new TransmitterMovementBehaviour());
        MovingInteractionBehaviour.REGISTRY.register(DKBlocks.TRANSMITTER.get(), new TransmitterMovingInteraction());
    }
}
