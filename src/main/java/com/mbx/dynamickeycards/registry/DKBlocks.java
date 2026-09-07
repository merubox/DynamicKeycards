package com.mbx.dynamickeycards.registry;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.block.AdvancedCeilingSensorBlock;
import com.mbx.dynamickeycards.block.AdvancedWallSensorBlock;
import com.mbx.dynamickeycards.block.CardDuplicatorBlock;
import com.mbx.dynamickeycards.block.CardReaderBlock;
import com.mbx.dynamickeycards.block.CeilingSensorBlock;
import com.mbx.dynamickeycards.block.ReceiverBlock;
import com.mbx.dynamickeycards.block.SirenBlock;
import com.mbx.dynamickeycards.block.TransmitterBlock;
import com.mbx.dynamickeycards.block.WallSensorBlock;
import com.mbx.dynamickeycards.item.BoundSensorBlockItem;
import com.mbx.dynamickeycards.item.LinkedReaderBlockItem;
import com.mbx.dynamickeycards.item.ReceiverBlockItem;
import com.mbx.dynamickeycards.item.SensorBlockItem;
import com.mbx.dynamickeycards.item.SirenBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * The four card reader variants share one block class; they differ only in looks
 * (insert slot, touch glass, swipe groove, advanced display).
 */
public class DKBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DynamicKeycards.MOD_ID);

    /**
     * Every block's item form, in declaration order - {@link DKCreativeTabs} reads this instead
     * of listing each block a second time by hand, so a block registered here but left off that
     * separate list can't silently just never show up in the creative tab. Kept apart from
     * {@link DKItems#TAB_ITEMS} (rather than appending directly into that one shared list) since
     * registering a block here is what first triggers {@code DKItems} to class-load at all (see
     * {@link #register(String, java.util.function.Supplier, BiFunction)}) - by the time control
     * returns here, all of {@code DKItems}' own entries already exist, so appending into its list
     * would always land after every card regardless of which block triggered it, silently
     * reordering the tab. Two lists read in a fixed order avoids depending on this at all.
     */
    public static final List<DeferredItem<Item>> TAB_BLOCKS = new ArrayList<>();

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

    /** No local physical redstone output of its own - see {@code TransmitterBlockEntity}'s own doc - so a plain {@link BlockItem} is enough. */
    public static final DeferredBlock<Block> TRANSMITTER = register("transmitter", () -> new TransmitterBlock(props()));
    /** The item form is a {@link ReceiverBlockItem} instead of a plain {@link BlockItem} so it can be bound to a source before placement. */
    public static final DeferredBlock<Block> RECEIVER = register("receiver",
            () -> new ReceiverBlock(props()), ReceiverBlockItem::new);

    /** Purely an output - the item form is a {@link SirenBlockItem} so it can be bound to a source before placement, but an unbound one still runs off plain redstone. */
    public static final DeferredBlock<Block> SIREN = register("siren",
            () -> new SirenBlock(sirenProps()), SirenBlockItem::new);

    private static DeferredBlock<Block> registerReader(String name) {
        return register(name, () -> new CardReaderBlock(props()), LinkedReaderBlockItem::new);
    }

    private static DeferredBlock<Block> register(String name, java.util.function.Supplier<Block> factory) {
        return register(name, factory, BlockItem::new);
    }

    private static DeferredBlock<Block> register(String name, java.util.function.Supplier<Block> factory,
                                                   BiFunction<Block, Item.Properties, ? extends BlockItem> itemFactory) {
        DeferredBlock<Block> block = BLOCKS.register(name, factory);
        TAB_BLOCKS.add(DKItems.ITEMS.register(name, () -> itemFactory.apply(block.get(), new Item.Properties())));
        return block;
    }

    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of().noOcclusion().strength(0.5f).sound(SoundType.METAL);
    }

    /**
     * A spinning siren actually lights its surroundings, the way a torch does - without this the
     * lens only looks bright and the light it appears to cast falls on nothing. Tied to
     * {@link SirenBlock#LIT}, which only flips when the siren starts or stops, so the lighting
     * recalculation it triggers happens twice per activation rather than once per animation frame.
     */
    private static BlockBehaviour.Properties sirenProps() {
        return props().lightLevel(state -> state.getValue(SirenBlock.LIT) ? SIREN_LIT_LIGHT : 0);
    }

    /**
     * Deliberately dim. A block that emits light floors its <em>own</em> faces at that level -
     * {@code LevelRenderer.getLightColor} takes {@code max(block light, state.getLightEmission())},
     * and the light engine writes the value into the map at the block's own position, so there is
     * no exempting the metal base from it and no recovering what the light would have been without
     * it. Whatever this is, the base steps up by it the instant the siren starts turning, which a
     * shader pack reads as the base itself going luminous. At 4 that step is small enough not to
     * register, while the siren still puts a pool of light at its feet instead of dying in a dark
     * room. It was 10, which was too much to miss.
     */
    private static final int SIREN_LIT_LIGHT = 4;

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
