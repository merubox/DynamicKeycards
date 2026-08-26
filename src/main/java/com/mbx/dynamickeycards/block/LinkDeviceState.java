package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.compat.create.CreateAvailability;
import com.mbx.dynamickeycards.compat.create.CreateLinkCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Create Redstone Link plumbing every {@link LinkDeviceBlockEntity} shares - two ghost
 * frequency slots, a {@link SignalMode}, and the opaque {@link CreateLinkCompat} adapter - factored
 * out once {@code CardReaderBlockEntity} and {@code MotionSensorBlockEntity} had grown
 * byte-for-byte copies of the same fields and register/unregister plumbing. Owned by (not
 * extended from) each block entity: the two otherwise share nothing else worth a common base
 * class, and {@code AdvancedSensorBlockEntity} already extends {@code MotionSensorBlockEntity}
 * for unrelated reasons, so inserting a second shared ancestor above both would tangle the
 * hierarchy for no benefit composition doesn't already give.
 *
 * <p>Deliberately does not call the owner's own {@code syncToClient()} - each block entity still
 * calls that itself after every mutating method here, the same way it always did, since only the
 * owner knows its own sync mechanics. This class only owns the state and the Create-facing side
 * effects (register/unregister/notify) that follow directly from changing it.
 */
final class LinkDeviceState {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkDeviceState.class);

    private final LinkDeviceBlockEntity owner;
    private final ItemStack[] frequencySlots = {ItemStack.EMPTY, ItemStack.EMPTY};
    private SignalMode signalMode = SignalMode.NORMAL;
    @Nullable
    private Object createLinkAdapter;

    LinkDeviceState(LinkDeviceBlockEntity owner) {
        this.owner = owner;
    }

    ItemStack getFrequencySlot(int index) {
        return frequencySlots[index];
    }

    void setFrequencySlot(int index, ItemStack stack) {
        frequencySlots[index] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        reregisterLink();
    }

    SignalMode getSignalMode() {
        return signalMode;
    }

    void setSignalMode(SignalMode signalMode) {
        this.signalMode = signalMode;
        if (signalMode.linkActive) {
            registerLink();
        } else {
            unregisterLink();
        }
    }

    /** For {@link CardReaderBlockEntity#loadAdditional}'s pre-0.1.3 save migration only. */
    void setSignalModeRaw(SignalMode signalMode) {
        this.signalMode = signalMode;
    }

    /** Re-announces {@code owner} to Create's Redstone Link network under its current frequency. */
    private void reregisterLink() {
        try {
            Level level = owner.getLevel();
            if (createLinkAdapter != null && level != null) {
                CreateLinkCompat.unregister(level, createLinkAdapter);
                CreateLinkCompat.register(level, createLinkAdapter);
            }
        } catch (Throwable t) {
            LOGGER.warn("Skipping Create Redstone Link re-registration", t);
        }
    }

    /**
     * Guarded by {@link CreateLinkCompat#isLoaded} as always, but also wrapped in
     * {@code catch (Throwable)} - see {@code DKCommonSetup}'s own doc for why an {@code isLoaded}
     * guard alone isn't a complete guarantee here: the first time a class that touches Create
     * types actually gets used can still throw even on a path that's never reached at runtime, and
     * losing Create Redstone Link on one device is a far smaller problem than a broken menu
     * crashing the whole game out from under a player mid-session.
     */
    private void registerLink() {
        try {
            Level level = owner.getLevel();
            if (level == null || level.isClientSide || !CreateAvailability.isLoaded()) {
                return;
            }
            if (createLinkAdapter == null) {
                createLinkAdapter = CreateLinkCompat.createAdapter(owner);
            }
            CreateLinkCompat.register(level, createLinkAdapter);
        } catch (Throwable t) {
            LOGGER.warn("Skipping Create Redstone Link registration", t);
        }
    }

    private void unregisterLink() {
        try {
            Level level = owner.getLevel();
            if (createLinkAdapter != null && level != null) {
                CreateLinkCompat.unregister(level, createLinkAdapter);
            }
        } catch (Throwable t) {
            LOGGER.warn("Skipping Create Redstone Link unregistration", t);
        }
    }

    /**
     * Tells Create's Redstone Link network to re-poll {@code owner}'s transmitted strength
     * ({@link LinkDeviceBlockEntity#getLinkStrength}). A no-op unless Create is installed and
     * {@code owner} is currently registered.
     */
    void notifyLinkChanged() {
        try {
            Level level = owner.getLevel();
            if (createLinkAdapter != null && level != null) {
                CreateLinkCompat.notifyChanged(level, createLinkAdapter);
            }
        } catch (Throwable t) {
            LOGGER.warn("Skipping Create Redstone Link change notification", t);
        }
    }

    /** Call from the owner's own {@code onLoad}. */
    void onLoad() {
        if (signalMode.linkActive) {
            registerLink();
        }
    }

    /** Call from the owner's own {@code setRemoved}. */
    void setRemoved() {
        unregisterLink();
        createLinkAdapter = null;
    }

    void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (!frequencySlots[0].isEmpty()) {
            tag.put("FrequencySlot0", frequencySlots[0].save(registries));
        }
        if (!frequencySlots[1].isEmpty()) {
            tag.put("FrequencySlot1", frequencySlots[1].save(registries));
        }
        tag.putString("SignalMode", signalMode.name());
    }

    /** Loads frequency slots always, and {@link #signalMode} only when {@code tag} actually has one - see {@link #setSignalModeRaw}. */
    void load(CompoundTag tag, HolderLookup.Provider registries) {
        frequencySlots[0] = tag.contains("FrequencySlot0")
                ? ItemStack.parseOptional(registries, tag.getCompound("FrequencySlot0")) : ItemStack.EMPTY;
        frequencySlots[1] = tag.contains("FrequencySlot1")
                ? ItemStack.parseOptional(registries, tag.getCompound("FrequencySlot1")) : ItemStack.EMPTY;
        if (tag.contains("SignalMode")) {
            signalMode = SignalMode.byName(tag.getString("SignalMode"));
        }
    }
}
