package com.mbx.dynamickeycards.registry;

import com.mbx.dynamickeycards.DynamicKeycards;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@link #CARD_ID} is the unique key a blank keycard receives the first time it is
 * registered on a card reader; readers store these ids, so identical-looking keycards
 * with different ids stay distinct.
 */
public class DKComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, DynamicKeycards.MOD_ID);

    public static final Supplier<DataComponentType<UUID>> CARD_ID = COMPONENTS.register("card_id",
            () -> DataComponentType.<UUID>builder()
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC)
                    .build());
}
