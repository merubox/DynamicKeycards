package com.mbx.dynamickeycards.registry;

import com.mbx.dynamickeycards.DynamicKeycards;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class DKCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DynamicKeycards.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.dynamickeycards"))
                    .icon(() -> new ItemStack(DKItems.GOLDEN_KEYCARD.get()))
                    .displayItems((params, output) -> {
                        output.accept(DKBlocks.INSERT_CARD_READER.get());
                        output.accept(DKBlocks.TOUCH_CARD_READER.get());
                        output.accept(DKBlocks.SWIPE_CARD_READER.get());
                        output.accept(DKBlocks.ADVANCED_CARD_READER.get());
                        output.accept(DKBlocks.OBSIDIAN_CARD_READER.get());
                        output.accept(DKBlocks.CARD_DUPLICATOR.get());
                        DKItems.TAB_ITEMS.forEach(item -> output.accept(item.get()));
                    })
                    .build());
}
