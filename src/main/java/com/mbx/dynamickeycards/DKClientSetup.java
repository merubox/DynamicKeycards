package com.mbx.dynamickeycards;

import com.mbx.dynamickeycards.block.AdvancedSensorRenderer;
import com.mbx.dynamickeycards.menu.LinkDeviceScreen;
import com.mbx.dynamickeycards.registry.DKBlockEntities;
import com.mbx.dynamickeycards.registry.DKMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import org.jetbrains.annotations.Nullable;

/** Client-only registration (screens, renderers, ...). Never touched on a dedicated server. */
@EventBusSubscriber(modid = DynamicKeycards.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class DKClientSetup {

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(DKMenuTypes.LINK_DEVICE.get(), LinkDeviceScreen::new);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(DKBlockEntities.ADVANCED_SENSOR.get(), AdvancedSensorRenderer::new);
    }

    /**
     * The 17 accent looks (16 dyes + the native undyed "gold" one) x on/off x, for the wall
     * variant, 4 facings are never referenced by any blockstate ({@code AdvancedSensorRenderer}
     * looks them up directly by {@link net.minecraft.client.resources.model.ModelManager}), so
     * without this they'd never get baked in the first place.
     */
    @SubscribeEvent
    static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        for (String state : new String[]{"off", "on"}) {
            for (@Nullable DyeColor color : accentColors()) {
                String colorSegment = color == null ? "" : color.getSerializedName() + "_";
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    event.register(ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID,
                            "block/sensor/advanced_wall_sensor_" + colorSegment + state + "_" + facing.getSerializedName())));
                }
                event.register(ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID,
                        "block/sensor/advanced_ceiling_sensor_" + colorSegment + state)));
            }
        }
    }

    private static DyeColor[] accentColors() {
        DyeColor[] withGold = new DyeColor[DyeColor.values().length + 1];
        System.arraycopy(DyeColor.values(), 0, withGold, 1, DyeColor.values().length);
        withGold[0] = null; // the native undyed "gold" look
        return withGold;
    }
}
