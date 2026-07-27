package com.mbx.dynamickeycards;

import com.mbx.dynamickeycards.menu.CardReaderConfigScreen;
import com.mbx.dynamickeycards.registry.DKMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only registration (screens, renderers, ...). Never touched on a dedicated server. */
@EventBusSubscriber(modid = DynamicKeycards.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class DKClientSetup {

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(DKMenuTypes.BROADCAST_MODE.get(), CardReaderConfigScreen::new);
    }
}
