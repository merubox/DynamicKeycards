package com.mbx.dynamickeycards.item;

import com.mbx.dynamickeycards.registry.DKComponents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A block item that can be tuned to a {@code block.SignalSource} before it's placed - the
 * receiver and the siren. Both store the id in the same {@code DKComponents.BOUND_SOURCE}
 * component and are carried into the placed block entity by their own {@code setPlacedBy}; this
 * interface exists only so {@code SignalSource#tryBindReceiverItem} can hand a source id to
 * either one without naming both.
 */
public interface SourceBindableItem {

    void bindTo(ItemStack stack, UUID sourceId);

    /**
     * What {@code stack} is currently tuned to, or {@code null}. Static rather than an instance
     * method because most callers are asking about an arbitrary held stack that may not be one of
     * these items at all - it answers {@code null} for those instead of making every caller
     * type-check first.
     */
    @Nullable
    static UUID boundSource(ItemStack stack) {
        return stack.getItem() instanceof SourceBindableItem ? stack.get(DKComponents.BOUND_SOURCE.get()) : null;
    }
}
