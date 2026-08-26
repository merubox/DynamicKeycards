package com.mbx.dynamickeycards.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * The shared menu behind {@link AbstractDeviceModeScreen} - the server-side half of a device whose
 * whole UI is "pick one of N modes, plus one tick-valued duration" ({@link ReceiverMenu},
 * {@link TransmitterMenu}).
 *
 * <p>Deliberately independent of {@code LinkDeviceMenu}: no ghost frequency slots, no
 * {@code LinkDeviceBlockEntity}/{@code LinkDeviceState} plumbing, so clicking a mode button here
 * can never accidentally register with Create's Redstone Link network - neither device has
 * anything to do with it. Just the player's own inventory (3x9 + hotbar), at the exact same
 * coordinates {@code LinkDeviceMenu} uses, since the screens share its background canvas.
 *
 * <p><b>Button ids.</b> {@code 0..n-1} select a mode, and are the same indices as
 * {@link #modeSetters()} - and as {@link AbstractDeviceModeScreen#modes()}, whose buttons are laid
 * out left to right in that order. So "the third mode button" means index 2 in both lists and
 * button id 2; keep the two lists in the same order. {@link #BUTTON_RESET} follows the modes, and
 * anything from {@link #DURATION_ID_BASE} up is a duration value rather than a button (see
 * {@code LinkDeviceMenu.SIGNAL_LENGTH_ID_BASE}, the same trick).
 */
public abstract class AbstractDeviceModeMenu<D extends BlockEntity> extends AbstractContainerMenu {

    /** Restores the duration to this device's default - see {@link #resetDurationTicks()}. */
    public static final int BUTTON_RESET = 2;
    /** Duration values are sent as {@code DURATION_ID_BASE + ticks}. */
    public static final int DURATION_ID_BASE = 10_000;
    /** One hour, matching the picker's own ceiling ({@code DurationPopup.MAX_TICKS}). */
    private static final int MAX_DURATION_TICKS = 72_000;

    /** Where the player-inventory panel's slots sit on the shared background canvas. */
    private static final int INV_X = 8;
    private static final int INV_Y = 121;
    private static final int HOTBAR_Y = 179;
    private static final int SLOT_SIZE = 18;
    /** Slot index one past the last main-inventory slot - the hotbar starts here. */
    private static final int HOTBAR_START = 27;
    private static final int SLOT_COUNT = 36;

    private final D device;

    protected AbstractDeviceModeMenu(MenuType<?> type, int containerId, Inventory playerInventory, D device) {
        super(type, containerId);
        this.device = device;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        INV_X + col * SLOT_SIZE, INV_Y + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, INV_X + col * SLOT_SIZE, HOTBAR_Y));
        }
    }

    public D getDevice() {
        return device;
    }

    // ---- Subclass contract ----

    /**
     * Applies each mode, in button-id order - see the class doc for how this lines up with
     * {@link AbstractDeviceModeScreen#modes()}.
     */
    protected abstract List<Runnable> modeSetters();

    protected abstract int durationTicks();

    protected abstract void setDurationTicks(int ticks);

    /** Restores the duration to this device's own default, for {@link #BUTTON_RESET}. */
    protected abstract void resetDurationTicks();

    // ---- Button handling ----

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= DURATION_ID_BASE) {
            // 0 is a legitimate value (instant cutoff) - not clamped up to 1, same as
            // LinkDeviceMenu's signal length
            int ticks = Math.clamp(id - DURATION_ID_BASE, 0, MAX_DURATION_TICKS);
            if (ticks != durationTicks()) {
                setDurationTicks(ticks);
                playDurationConfirmSound();
            }
            return true;
        }
        if (id == BUTTON_RESET) {
            resetDurationTicks();
            return true;
        }
        List<Runnable> setters = modeSetters();
        if (id >= 0 && id < setters.size()) {
            setters.get(id).run();
            return true;
        }
        return false;
    }

    /** Same paired-sound treatment as {@link LinkDeviceMenu#playPulseConfirmSound}. */
    private void playDurationConfirmSound() {
        Level level = device.getLevel();
        BlockPos pos = device.getBlockPos();
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.25f, 2f);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), SoundSource.BLOCKS, 0.03f, 1.125f);
    }

    // ---- Container plumbing ----

    /** No non-inventory slots to shift-click into - just shuffles between the main inventory and hotbar, same as any plain container. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stackInSlot = slot.getItem();
        ItemStack result = stackInSlot.copy();
        boolean moved = index < HOTBAR_START
                ? this.moveItemStackTo(stackInSlot, HOTBAR_START, SLOT_COUNT, true)
                : this.moveItemStackTo(stackInSlot, 0, HOTBAR_START, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stackInSlot.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        Level level = device.getLevel();
        BlockPos pos = device.getBlockPos();
        return level != null && level.getBlockEntity(pos) == device
                && Container.stillValidBlockEntity(device, player);
    }
}
