package com.mbx.dynamickeycards;

import com.mbx.dynamickeycards.compat.create.CreateAvailability;
import com.mbx.dynamickeycards.compat.create.CreateMovementCompat;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (client+server) mod-init hook. Registers this mod's blocks against Create's own per-block
 * {@code MovementBehaviour}/{@code MovingInteractionBehaviour} registries, if Create is loaded -
 * see {@link CreateMovementCompat} for what that actually wires up. Runs once, after block
 * registration ({@code registry.DKBlocks}) has already completed.
 *
 * <p><b>Don't simplify the reflection or the {@code catch (Throwable)} away.</b> Reaching
 * {@link CreateMovementCompat} at all links it, and its {@code register()} body touches Create
 * types - so with Create absent that throws even though the method never runs. The catch is the
 * last line of defence: losing moving-contraption support beats the game refusing to start. See
 * {@code ROADMAP.md}, "Create 부재 시 클래스로딩 크래시", for why three earlier fixes didn't work.
 */
@EventBusSubscriber(modid = DynamicKeycards.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class DKCommonSetup {

    private static final Logger LOGGER = LoggerFactory.getLogger(DKCommonSetup.class);

    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(DKCommonSetup::registerCreateCompat);
    }

    private static void registerCreateCompat() {
        try {
            if (CreateAvailability.isLoaded()) {
                doRegisterCreateCompat();
            }
        } catch (Throwable t) {
            LOGGER.warn("Skipping Create movement compat registration - Create moving-contraption "
                    + "support for readers/transmitters will be unavailable this session", t);
        }
    }

    private static void doRegisterCreateCompat() throws ReflectiveOperationException {
        Class.forName("com.mbx.dynamickeycards.compat.create.CreateMovementCompat")
                .getMethod("register")
                .invoke(null);
    }
}
