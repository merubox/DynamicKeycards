package com.mbx.dynamickeycards.registry;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.block.AdvancedCeilingSensorBlock;
import com.mbx.dynamickeycards.block.AdvancedWallSensorBlock;
import com.mbx.dynamickeycards.block.CardDuplicatorBlock;
import com.mbx.dynamickeycards.block.CardReaderBlock;
import com.mbx.dynamickeycards.block.CeilingSensorBlock;
import com.mbx.dynamickeycards.block.WallSensorBlock;
import com.mbx.dynamickeycards.item.BoundSensorBlockItem;
import com.mbx.dynamickeycards.item.LinkedReaderBlockItem;
import com.mbx.dynamickeycards.item.SensorBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.BiFunction;

/**
 * The four card reader variants share one block class; they differ only in looks
 * (insert slot, touch glass, swipe groove, advanced display).
 */
public class DKBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DynamicKeycards.MOD_ID);

    public static final DeferredBlock<Block> INSERT_CARD_READER = registerReader("insert_card_reader");
    public static final DeferredBlock<Block> TOUCH_CARD_READER = registerReader("touch_card_reader");
    public static final DeferredBlock<Block> SWIPE_CARD_READER = registerReader("swipe_card_reader");
    public static final DeferredBlock<Block> ADVANCED_CARD_READER = registerReader("advanced_card_reader");
    /** Pricier recipe (gold, a hopper, a redstone lamp, obsidian); mines as slowly as obsidian itself. */
    public static final DeferredBlock<Block> OBSIDIAN_CARD_READER = register("obsidian_card_reader",
            () -> new CardReaderBlock(obsidianProps()), LinkedReaderBlockItem::new);

    public static final DeferredBlock<Block> CARD_DUPLICATOR = register("card_duplicator", () -> new CardDuplicatorBlock(props()));

    public static final DeferredBlock<Block> WALL_SENSOR = register("wall_sensor",
            () -> new WallSensorBlock(props()), SensorBlockItem::new);
    public static final DeferredBlock<Block> CEILING_SENSOR = register("ceiling_sensor",
            () -> new CeilingSensorBlock(props()), SensorBlockItem::new);

    /**
     * Pricier, sturdier than the plain sensors (see {@code advancedSensorProps}); the item form
     * is a {@link BoundSensorBlockItem} instead of a plain {@link BlockItem} so it can be bound
     * to a card reader before placement.
     */
    public static final DeferredBlock<Block> ADVANCED_WALL_SENSOR = register("advanced_wall_sensor",
            () -> new AdvancedWallSensorBlock(advancedSensorProps()), BoundSensorBlockItem::new);
    public static final DeferredBlock<Block> ADVANCED_CEILING_SENSOR = register("advanced_ceiling_sensor",
            () -> new AdvancedCeilingSensorBlock(advancedSensorProps()), BoundSensorBlockItem::new);

    private static DeferredBlock<Block> registerReader(String name) {
        return register(name, () -> new CardReaderBlock(props()), LinkedReaderBlockItem::new);
    }

    private static DeferredBlock<Block> register(String name, java.util.function.Supplier<Block> factory) {
        return register(name, factory, BlockItem::new);
    }

    private static DeferredBlock<Block> register(String name, java.util.function.Supplier<Block> factory,
                                                   BiFunction<Block, Item.Properties, ? extends BlockItem> itemFactory) {
        DeferredBlock<Block> block = BLOCKS.register(name, factory);
        DKItems.ITEMS.register(name, () -> itemFactory.apply(block.get(), new Item.Properties()));
        return block;
    }

    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of().noOcclusion().strength(0.5f).sound(SoundType.METAL);
    }

    /** Same hardness/resistance as vanilla obsidian; needs the same tool tier as obsidian too. */
    private static BlockBehaviour.Properties obsidianProps() {
        return BlockBehaviour.Properties.of().noOcclusion().strength(50.0f, 1200.0f)
                .sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    /** Sturdier than the plain sensors, matching the pricier recipe - iron tools required. */
    private static BlockBehaviour.Properties advancedSensorProps() {
        return BlockBehaviour.Properties.of().noOcclusion().strength(2.0f)
                .sound(SoundType.METAL).requiresCorrectToolForDrops();
    }
}
