package com.mbx.dynamickeycards.registry;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.block.AdvancedSensorBlockEntity;
import com.mbx.dynamickeycards.block.CardDuplicatorBlockEntity;
import com.mbx.dynamickeycards.block.CardReaderBlockEntity;
import com.mbx.dynamickeycards.block.MotionSensorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class DKBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DynamicKeycards.MOD_ID);

    public static final Supplier<BlockEntityType<CardReaderBlockEntity>> CARD_READER =
            BLOCK_ENTITIES.register("card_reader", () -> BlockEntityType.Builder.of(CardReaderBlockEntity::new,
                    DKBlocks.INSERT_CARD_READER.get(),
                    DKBlocks.TOUCH_CARD_READER.get(),
                    DKBlocks.SWIPE_CARD_READER.get(),
                    DKBlocks.ADVANCED_CARD_READER.get(),
                    DKBlocks.OBSIDIAN_CARD_READER.get()).build(null));

    public static final Supplier<BlockEntityType<CardDuplicatorBlockEntity>> CARD_DUPLICATOR =
            BLOCK_ENTITIES.register("card_duplicator", () -> BlockEntityType.Builder.of(CardDuplicatorBlockEntity::new,
                    DKBlocks.CARD_DUPLICATOR.get()).build(null));

    public static final Supplier<BlockEntityType<MotionSensorBlockEntity>> MOTION_SENSOR =
            BLOCK_ENTITIES.register("motion_sensor", () -> BlockEntityType.Builder.of(MotionSensorBlockEntity::new,
                    DKBlocks.WALL_SENSOR.get(), DKBlocks.CEILING_SENSOR.get()).build(null));

    public static final Supplier<BlockEntityType<AdvancedSensorBlockEntity>> ADVANCED_SENSOR =
            BLOCK_ENTITIES.register("advanced_sensor", () -> BlockEntityType.Builder.of(AdvancedSensorBlockEntity::new,
                    DKBlocks.ADVANCED_WALL_SENSOR.get(), DKBlocks.ADVANCED_CEILING_SENSOR.get()).build(null));
}
