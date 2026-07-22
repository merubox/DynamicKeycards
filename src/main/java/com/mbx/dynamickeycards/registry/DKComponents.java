package com.mbx.dynamickeycards.registry;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mojang.serialization.Codec;
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

    /**
     * The player an Estate Keycard is bound to. It behaves like a golden keycard, but only
     * on readers owned by this player — regardless of who currently holds the card.
     */
    public static final Supplier<DataComponentType<UUID>> BOUND_OWNER = COMPONENTS.register("bound_owner",
            () -> DataComponentType.<UUID>builder()
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC)
                    .build());

    /**
     * Game-time deadline for the two-step Estate Keycard activation: the first right-click
     * sets it, and a second right-click before it passes binds the card. Expires on its own
     * so a stale first click never binds unexpectedly.
     */
    public static final Supplier<DataComponentType<Long>> ACTIVATION_DEADLINE = COMPONENTS.register("activation_deadline",
            () -> DataComponentType.<Long>builder()
                    .persistent(Codec.LONG)
                    .networkSynchronized(ByteBufCodecs.VAR_LONG)
                    .build());
}
