package com.mbx.dynamickeycards.compat.create;

import com.mbx.dynamickeycards.block.AdvancedSensorBlockEntity;
import com.mbx.dynamickeycards.block.CardReaderBlockEntity;
import com.mbx.dynamickeycards.registry.DKBlockEntities;
import com.simibubi.create.api.contraption.transformable.MovedBlockTransformerRegistries;

/**
 * Registers our block entities with Create's structure-transform system, so a linked/bound
 * position survives being moved as a whole - a Create schematic printed at an offset or
 * rotation, or a contraption that's assembled, moved, and disassembled elsewhere. Without this,
 * {@code CardReaderBlockEntity#linkedReaderPos}/{@code AdvancedSensorBlockEntity#boundReaderPos}/
 * {@code #boundSensorPos} are plain world-absolute {@link net.minecraft.core.BlockPos} values
 * with nothing that would otherwise know to update them, so they'd keep pointing at wherever the
 * other end was at capture time - silently wrong (not crashing) after the move, since every read
 * site already treats "nothing of the right type there" as simply unlinked.
 *
 * <p>Only ever called from {@link com.mbx.dynamickeycards.DKCommonSetup} once Create is confirmed
 * loaded - see {@link CreateLinkCompat} for the same isolation reasoning (every Create type stays
 * inside this package).
 */
public final class PositionTransformCompat {

    private PositionTransformCompat() {
    }

    public static void register() {
        MovedBlockTransformerRegistries.BLOCK_ENTITY_TRANSFORMERS.register(DKBlockEntities.CARD_READER.get(),
                (be, transform) -> {
                    if (be instanceof CardReaderBlockEntity reader) {
                        reader.applyPositionTransform(transform::apply);
                    }
                });
        MovedBlockTransformerRegistries.BLOCK_ENTITY_TRANSFORMERS.register(DKBlockEntities.ADVANCED_SENSOR.get(),
                (be, transform) -> {
                    if (be instanceof AdvancedSensorBlockEntity sensor) {
                        sensor.applyPositionTransform(transform::apply);
                    }
                });
    }
}
