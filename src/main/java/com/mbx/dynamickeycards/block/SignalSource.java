package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DKNetwork;

import com.mbx.dynamickeycards.DKSounds;
import com.mbx.dynamickeycards.item.SourceBindableItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Anything a {@code ReceiverBlockEntity} (or another device's own binding, e.g. an advanced
 * sensor bound to a reader) can listen to over this mod's own wireless system: a card reader, an
 * advanced sensor, or a transmitter. Implementors publish a raw, momentary strength - never
 * shaped by their own local pulse length or release delay (see {@link #getSignalSourceStrength}) -
 * into the shared {@link DeviceIndex}, so a listener never has to touch the source's actual
 * {@code BlockEntity} to know its current value.
 */
public interface SignalSource {

    UUID getDeviceId();

    /**
     * This device's current raw output, 0-15 - deliberately independent of whatever local
     * duration/decay settings this same device applies to its own physical redstone (a reader's
     * configured accept-signal length, a sensor's release delay): those exist to shape *that*
     * device's own wire, not what gets broadcast to the wireless system. A listener decides for
     * itself how long to hold, pulse, or toggle in response - see {@code ReceiverBlockEntity}.
     */
    int getSignalSourceStrength();

    /**
     * Publishes {@link #getSignalSourceStrength} into the shared index - a no-op on the client.
     * On an actual change, this also immediately pushes the new value to every bound receiver
     * (see {@link DeviceIndex#updateSignal}), rather than each one having to wait out its own next
     * tick to notice.
     */
    default void publishSignalSource(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            DeviceIndex.get(serverLevel).updateSignal(getDeviceId(), getSignalSourceStrength(), serverLevel);
        }
    }

    /**
     * Shared held-item handling for a still-unplaced {@link SourceBindableItem} (a receiver or a
     * siren) right-clicking {@code source} - binds it and announces it, same
     * white "tuned" convention {@code BoundSensorBlockItem}/{@code LinkedReaderBlockItem} binding
     * already uses. {@code null} if {@code stack} isn't one, the same "not applicable" convention
     * used throughout this mod's item-interaction dispatch. Callers that need to gate this behind
     * some kind of consent (the card reader's register mode) check it themselves before calling -
     * a transmitter or an advanced sensor *not currently bound to a reader* has no owner to
     * protect, so they call this unconditionally.
     *
     * <p>An advanced sensor that <em>is</em> currently bound to a reader is refused here
     * unconditionally, regardless of caller - a receiver is only ever meant to plug straight into
     * a reader (or a transmitter), never sneak in through a peripheral the reader itself already
     * trusts. Letting that through would hand anyone (a bound sensor has no owner of its own to
     * gate this with) a side-channel wireless tap into whatever that reader is protecting,
     * completely bypassing the reader owner's own consent (register mode) that binding straight
     * to the reader already requires.
     */
    @Nullable
    static ItemInteractionResult tryBindReceiverItem(ItemStack stack, Level level, BlockPos pos, Player player, SignalSource source) {
        if (!(stack.getItem() instanceof SourceBindableItem bindableItem)) {
            return null;
        }
        if (source instanceof AdvancedSensorBlockEntity advanced && advanced.getBoundReader() != null) {
            if (!level.isClientSide) {
                // same "already claimed" message as any other already-bound target (see
                // MotionSensorBlock#tryBindItemInteraction) - this sensor's wireless slot is
                // already spoken for by its reader binding, same underlying concept
                player.displayClientMessage(
                        Component.translatable("dynamickeycards.link_device.already_bound").withStyle(ChatFormatting.RED), true);
                DKSounds.deny(level, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            bindableItem.bindTo(stack, source.getDeviceId());
            // forces the held-item resync immediately - see CardReaderBlock's sensor-bind case
            // for why this matters (otherwise the bind-target highlight can miss the moment of
            // binding, only catching up on the next automatic per-tick sync or a reconnect)
            player.containerMenu.broadcastChanges();
            DKNetwork.registerDevicePosition(player, source.getDeviceId(), pos);
            // white, not green: this only tunes the held item, the actual connection isn't
            // "complete" (green) until it's placed - same convention as BoundSensorBlockItem/
            // LinkedReaderBlockItem binding
            player.displayClientMessage(
                    Component.translatable("dynamickeycards.link_device.tuned").withStyle(ChatFormatting.WHITE), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
