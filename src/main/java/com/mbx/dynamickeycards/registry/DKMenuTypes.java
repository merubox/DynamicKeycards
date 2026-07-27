package com.mbx.dynamickeycards.registry;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.menu.CardReaderConfigMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class DKMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, DynamicKeycards.MOD_ID);

    /**
     * Card reader's wrench-opened config UI (link mode's Redstone Link frequency slots, plus
     * pulse length). Registered unconditionally — it's a plain vanilla menu with no Create types
     * in its signature — but only ever opened from {@code CardReaderBlock} when Create is loaded.
     */
    public static final Supplier<MenuType<CardReaderConfigMenu>> BROADCAST_MODE =
            MENU_TYPES.register("broadcast_mode", () -> IMenuTypeExtension.create(CardReaderConfigMenu::fromNetwork));
}
