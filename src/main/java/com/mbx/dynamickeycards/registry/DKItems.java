package com.mbx.dynamickeycards.registry;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.item.BlankKeycardItem;
import com.mbx.dynamickeycards.item.CrewManagerKeycardItem;
import com.mbx.dynamickeycards.item.CrewMemberKeycardItem;
import com.mbx.dynamickeycards.item.EstateKeycardItem;
import com.mbx.dynamickeycards.item.GoldenKeycardItem;
import com.mbx.dynamickeycards.item.KeycardItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class DKItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DynamicKeycards.MOD_ID);

    /** Vanilla dye color order used throughout the mod. */
    public static final List<String> COLORS = List.of(
            "white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
            "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink");

    /** Creative tab contents in declaration order. */
    public static final List<Supplier<? extends ItemLike>> TAB_ITEMS = new ArrayList<>();

    /**
     * Parallel to {@link #COLORS}: blank cards (craftable) and the keyed keycards they become,
     * plus the crew manager/member access cards. Only the crew cards use the "access card"
     * name; the ordinary keyed result stays a plain keycard.
     */
    public static final List<DeferredItem<Item>> BLANK_CARDS = new ArrayList<>();
    public static final List<DeferredItem<Item>> KEYCARDS = new ArrayList<>();
    public static final List<DeferredItem<Item>> MANAGER_CARDS = new ArrayList<>();
    public static final List<DeferredItem<Item>> MEMBER_CARDS = new ArrayList<>();

    public static final DeferredItem<Item> GOLDEN_KEYCARD;
    public static final DeferredItem<Item> ESTATE_KEYCARD;

    static {
        // Everything is shown in the creative tab; the keyed/member results are also obtainable
        // in survival through registration/duplication.
        for (String color : COLORS) {
            BLANK_CARDS.add(register(color + "_blank_card", () -> new BlankKeycardItem(new Item.Properties())));
        }
        for (String color : COLORS) {
            KEYCARDS.add(register(color + "_keycard", () -> new KeycardItem(new Item.Properties())));
        }
        for (String color : COLORS) {
            MANAGER_CARDS.add(register(color + "_manager_access_card", () -> new CrewManagerKeycardItem(new Item.Properties())));
        }
        for (String color : COLORS) {
            MEMBER_CARDS.add(register(color + "_member_access_card", () -> new CrewMemberKeycardItem(new Item.Properties())));
        }
        GOLDEN_KEYCARD = register("golden_keycard",
                () -> new GoldenKeycardItem(new Item.Properties().stacksTo(1)));
        ESTATE_KEYCARD = register("estate_keycard",
                () -> new EstateKeycardItem(new Item.Properties().stacksTo(1)));
    }

    /** Index of this card's color in {@link #COLORS}, or 0 if unrecognized. */
    public static int colorIndex(ItemStack stack) {
        for (int i = 0; i < COLORS.size(); i++) {
            if (stack.is(BLANK_CARDS.get(i).get()) || stack.is(KEYCARDS.get(i).get())
                    || stack.is(MANAGER_CARDS.get(i).get()) || stack.is(MEMBER_CARDS.get(i).get())) {
                return i;
            }
        }
        return 0;
    }

    /** The keyed keycard item of the same color as the given (typically blank) card. */
    public static Item keycardFor(ItemStack stack) {
        return KEYCARDS.get(colorIndex(stack)).get();
    }

    /** The member access card item of the same color as the given (typically blank) card. */
    public static Item memberCardFor(ItemStack stack) {
        return MEMBER_CARDS.get(colorIndex(stack)).get();
    }

    /** The blank card item of the same color — the recycled result of dyeing. */
    public static Item blankCardFor(ItemStack stack) {
        return BLANK_CARDS.get(colorIndex(stack)).get();
    }

    private static DeferredItem<Item> register(String name, Supplier<Item> item) {
        DeferredItem<Item> holder = ITEMS.register(name, item);
        TAB_ITEMS.add(holder);
        return holder;
    }
}
