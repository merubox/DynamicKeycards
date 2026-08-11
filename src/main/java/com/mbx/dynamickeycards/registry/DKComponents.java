package com.mbx.dynamickeycards.registry;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
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

    /**
     * The reader an unplaced advanced sensor item is bound to (set by right-clicking a card
     * reader while holding it, see {@code BoundSensorBlockItem}) - carried over into the block
     * entity on placement. Absent means "not yet bound".
     */
    public static final Supplier<DataComponentType<BlockPos>> BOUND_READER = COMPONENTS.register("bound_reader",
            () -> DataComponentType.<BlockPos>builder()
                    .persistent(BlockPos.CODEC)
                    .networkSynchronized(BlockPos.STREAM_CODEC)
                    .build());

    /**
     * The reader an unplaced reader item is set to link with (set by right-clicking an existing
     * card reader while holding it, see {@code LinkedReaderBlockItem}) - carried over into the
     * block entity on placement, at which point both readers point to each other. Absent means
     * "not yet linked".
     */
    public static final Supplier<DataComponentType<BlockPos>> LINKED_READER = COMPONENTS.register("linked_reader",
            () -> DataComponentType.<BlockPos>builder()
                    .persistent(BlockPos.CODEC)
                    .networkSynchronized(BlockPos.STREAM_CODEC)
                    .build());

    /**
     * The sensor an unplaced advanced sensor item is set to bind with (set by right-clicking an
     * existing wall/ceiling sensor - plain or advanced - while holding it, see
     * {@code BoundSensorBlockItem}) - carried over into the block entity on placement. Mutually
     * exclusive with {@link #BOUND_READER}: setting one clears the other, since a sensor can only
     * be bound to one thing at a time. Absent means "not yet bound to a sensor".
     */
    public static final Supplier<DataComponentType<BlockPos>> BOUND_SENSOR = COMPONENTS.register("bound_sensor",
            () -> DataComponentType.<BlockPos>builder()
                    .persistent(BlockPos.CODEC)
                    .networkSynchronized(BlockPos.STREAM_CODEC)
                    .build());

    /**
     * The sensor a redstone dust stack armed a range edit on (set by right-clicking it, see
     * {@code MotionSensorBlock#tryRangeEditInteraction}; cleared on confirm/cancel, see
     * {@code DKNetwork#handleCommit}) - alongside {@link net.minecraft.core.component.DataComponents#ENCHANTMENT_GLINT_OVERRIDE}
     * marking that stack as spoken for. Client-side, this is what the highlight's visibility
     * actually keys off (see {@code SensorRangeClientHandler}) - not proximity or where the
     * cursor is pointing, same as {@link #BOUND_READER}/{@link #LINKED_READER} already drive
     * their own held-item highlight in {@code DKClientEvents}.
     */
    public static final Supplier<DataComponentType<BlockPos>> RANGE_EDIT_TARGET = COMPONENTS.register("range_edit_target",
            () -> DataComponentType.<BlockPos>builder()
                    .persistent(BlockPos.CODEC)
                    .networkSynchronized(BlockPos.STREAM_CODEC)
                    .build());
}
