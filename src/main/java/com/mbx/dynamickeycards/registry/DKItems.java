package com.mbx.dynamickeycards.registry;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.item.GoldenKeycardItem;
import com.mbx.dynamickeycards.item.KeycardItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class DKItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DynamicKeycards.MOD_ID);

    /** Creative tab contents in declaration order: readers first, then keycards. */
    public static final List<Supplier<? extends ItemLike>> TAB_ITEMS = new ArrayList<>();

    public static final DeferredItem<Item> WHITE_KEYCARD = registerKeycard("white");
    public static final DeferredItem<Item> LIGHT_GRAY_KEYCARD = registerKeycard("light_gray");
    public static final DeferredItem<Item> GRAY_KEYCARD = registerKeycard("gray");
    public static final DeferredItem<Item> BLACK_KEYCARD = registerKeycard("black");
    public static final DeferredItem<Item> BROWN_KEYCARD = registerKeycard("brown");
    public static final DeferredItem<Item> RED_KEYCARD = registerKeycard("red");
    public static final DeferredItem<Item> ORANGE_KEYCARD = registerKeycard("orange");
    public static final DeferredItem<Item> YELLOW_KEYCARD = registerKeycard("yellow");
    public static final DeferredItem<Item> LIME_KEYCARD = registerKeycard("lime");
    public static final DeferredItem<Item> GREEN_KEYCARD = registerKeycard("green");
    public static final DeferredItem<Item> CYAN_KEYCARD = registerKeycard("cyan");
    public static final DeferredItem<Item> LIGHT_BLUE_KEYCARD = registerKeycard("light_blue");
    public static final DeferredItem<Item> BLUE_KEYCARD = registerKeycard("blue");
    public static final DeferredItem<Item> PURPLE_KEYCARD = registerKeycard("purple");
    public static final DeferredItem<Item> MAGENTA_KEYCARD = registerKeycard("magenta");
    public static final DeferredItem<Item> PINK_KEYCARD = registerKeycard("pink");

    public static final DeferredItem<Item> GOLDEN_KEYCARD = register("golden_keycard",
            () -> new GoldenKeycardItem(new Item.Properties().stacksTo(1)));

    private static DeferredItem<Item> registerKeycard(String color) {
        return register(color + "_keycard", () -> new KeycardItem(new Item.Properties()));
    }

    private static DeferredItem<Item> register(String name, Supplier<Item> item) {
        DeferredItem<Item> holder = ITEMS.register(name, item);
        TAB_ITEMS.add(holder);
        return holder;
    }
}
