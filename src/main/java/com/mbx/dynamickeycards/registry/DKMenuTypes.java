package com.mbx.dynamickeycards.registry;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.menu.LinkDeviceMenu;
import com.mbx.dynamickeycards.menu.ReceiverMenu;
import com.mbx.dynamickeycards.menu.TransmitterMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class DKMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, DynamicKeycards.MOD_ID);

    /**
     * Wrench-opened config UI shared by the card reader and both motion sensors (link mode's
     * Redstone Link frequency slots, plus signal length). Registered unconditionally — it's a
     * plain vanilla menu with no Create types in its signature — but only ever opened from a
     * device's own block class when Create is loaded.
     */
    public static final Supplier<MenuType<LinkDeviceMenu>> LINK_DEVICE =
            MENU_TYPES.register("broadcast_mode", () -> IMenuTypeExtension.create(LinkDeviceMenu::fromNetwork));

    /** Sneak-opened mode UI for a transmitter - visually reuses {@link LinkDeviceMenu}'s screen but is otherwise independent of it, see {@code menu.TransmitterMenu}'s own doc. */
    public static final Supplier<MenuType<TransmitterMenu>> TRANSMITTER =
            MENU_TYPES.register("transmitter", () -> IMenuTypeExtension.create(TransmitterMenu::fromNetwork));

    /** Same as {@link #TRANSMITTER}, for the receiver. */
    public static final Supplier<MenuType<ReceiverMenu>> RECEIVER =
            MENU_TYPES.register("receiver", () -> IMenuTypeExtension.create(ReceiverMenu::fromNetwork));
}
