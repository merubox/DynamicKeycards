package com.mbx.dynamickeycards.registry;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.menu.BroadcastModeMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class DKMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, DynamicKeycards.MOD_ID);

    /**
     * Card reader broadcast mode (Create Redstone Link frequency slots). Registered
     * unconditionally — it's a plain vanilla menu with no Create types in its signature — but
     * only ever opened from {@code CardReaderBlock} when Create is loaded. No screen is
     * registered for it yet; see {@code BroadcastModeMenu}'s class doc.
     */
    public static final Supplier<MenuType<BroadcastModeMenu>> BROADCAST_MODE =
            MENU_TYPES.register("broadcast_mode", () -> IMenuTypeExtension.create(BroadcastModeMenu::fromNetwork));
}
