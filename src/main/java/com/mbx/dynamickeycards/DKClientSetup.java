package com.mbx.dynamickeycards;

import com.mbx.dynamickeycards.client.KeycardNecklaceLayer;
import com.mbx.dynamickeycards.client.SirenRenderer;
import com.mbx.dynamickeycards.compat.accessories.AccessoriesAvailability;
import com.mbx.dynamickeycards.compat.curios.CuriosAvailability;
import com.mbx.dynamickeycards.menu.LinkDeviceScreen;
import com.mbx.dynamickeycards.menu.ReceiverScreen;
import com.mbx.dynamickeycards.menu.SirenScreen;
import com.mbx.dynamickeycards.menu.TransmitterScreen;
import com.mbx.dynamickeycards.registry.DKBlockEntities;
import com.mbx.dynamickeycards.registry.DKMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-only registration (menu screens, worn-card rendering). Never touched on a dedicated server. */
@EventBusSubscriber(modid = DynamicKeycards.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class DKClientSetup {

    private static final Logger LOGGER = LoggerFactory.getLogger(DKClientSetup.class);

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(DKMenuTypes.LINK_DEVICE.get(), LinkDeviceScreen::new);
        event.register(DKMenuTypes.TRANSMITTER.get(), TransmitterScreen::new);
        event.register(DKMenuTypes.RECEIVER.get(), ReceiverScreen::new);
        event.register(DKMenuTypes.SIREN.get(), SirenScreen::new);
    }

    /**
     * The siren's turning innards are drawn by {@link SirenRenderer}, not by any block state, so
     * nothing would otherwise load them - the model manager only bakes what a blockstate or an
     * item model reaches.
     */
    @SubscribeEvent
    static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        SirenRenderer.models().forEach(event::register);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(DKBlockEntities.SIREN.get(), SirenRenderer::new);
    }

    /** Registered unconditionally - the layer is pure vanilla, and an unused one costs nothing. */
    @SubscribeEvent
    static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        KeycardNecklaceLayer.register(event);
    }

    /**
     * Same guarded shape as the Create and Curios registrations in {@code DKCommonSetup}, for the
     * same reason - reaching {@code CuriosClientSetup} links it, and its body names Curios types.
     */
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            registerWornCardRenderers(CuriosAvailability.isLoaded(),
                    "com.mbx.dynamickeycards.compat.curios.CuriosClientSetup", "Curios");
            registerWornCardRenderers(AccessoriesAvailability.isLoaded(),
                    "com.mbx.dynamickeycards.compat.accessories.AccessoriesClientSetup", "Accessories");
        });
    }

    /** Named by string, not referenced: touching the class links it, and its body names that mod's types. */
    private static void registerWornCardRenderers(boolean loaded, String setupClass, String mod) {
        try {
            if (loaded) {
                Class.forName(setupClass).getMethod("registerRenderers").invoke(null);
            }
        } catch (Throwable t) {
            LOGGER.warn("Skipping worn-card rendering for {} - a card worn in its necklace slot "
                    + "will be invisible on the wearer this session", mod, t);
        }
    }

}
