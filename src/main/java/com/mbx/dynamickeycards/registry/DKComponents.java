package com.mbx.dynamickeycards.registry;

import com.mbx.dynamickeycards.DynamicKeycards;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
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

    /**
     * Keys inherited from ancestor cards through duplication. They still open readers
     * (any-match against the allow list), but registration always stamps only the card's
     * own {@link #CARD_ID}, so cards diverge after a copy — a true snapshot fork.
     */
    public static final Supplier<DataComponentType<List<UUID>>> INHERITED_KEYS = COMPONENTS.register("inherited_keys",
            () -> DataComponentType.<List<UUID>>builder()
                    .persistent(UUIDUtil.CODEC.listOf())
                    .networkSynchronized(UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()))
                    .build());
}
