package com.mbx.dynamickeycards.registry;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.block.CardReaderBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

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

    private static DeferredBlock<Block> registerReader(String name) {
        DeferredBlock<Block> block = BLOCKS.register(name, () -> new CardReaderBlock(
                BlockBehaviour.Properties.of().noOcclusion().strength(0.5f).sound(SoundType.METAL)));
        DKItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
}
