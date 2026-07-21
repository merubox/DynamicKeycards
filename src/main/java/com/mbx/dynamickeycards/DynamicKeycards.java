package com.mbx.dynamickeycards;

import com.mbx.dynamickeycards.registry.DKBlockEntities;
import com.mbx.dynamickeycards.registry.DKBlocks;
import com.mbx.dynamickeycards.registry.DKComponents;
import com.mbx.dynamickeycards.registry.DKCreativeTabs;
import com.mbx.dynamickeycards.registry.DKItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(DynamicKeycards.MOD_ID)
public class DynamicKeycards {
    public static final String MOD_ID = "dynamickeycards";

    public DynamicKeycards(IEventBus modBus) {
        DKComponents.COMPONENTS.register(modBus);
        DKBlocks.BLOCKS.register(modBus);
        DKItems.ITEMS.register(modBus);
        DKBlockEntities.BLOCK_ENTITIES.register(modBus);
        DKCreativeTabs.TABS.register(modBus);
    }
}
