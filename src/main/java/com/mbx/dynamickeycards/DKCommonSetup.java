package com.mbx.dynamickeycards;

import com.mbx.dynamickeycards.compat.create.CreateLinkCompat;
import com.mbx.dynamickeycards.compat.create.PositionTransformCompat;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * One-time setup that needs every registry already populated (unlike the mod constructor, which
 * only ever registers the {@code DeferredRegister}s themselves) - currently just the optional
 * Create hookups that touch another mod's own registry.
 */
@EventBusSubscriber(modid = DynamicKeycards.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class DKCommonSetup {

    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        if (CreateLinkCompat.isLoaded()) {
            event.enqueueWork(PositionTransformCompat::register);
        }
    }
}
