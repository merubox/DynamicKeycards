package com.mbx.dynamickeycards.menu;

import com.mbx.dynamickeycards.DKSounds;
import net.minecraft.core.BlockPos;
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
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * The shared menu behind {@link AbstractDeviceModeScreen} - the server-side half of a device whose
 * whole UI is "pick one of N modes, plus some tick-valued durations" ({@link ReceiverMenu},
 * {@link TransmitterMenu}, {@link SirenMenu}).
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
 * button id 2; keep the two lists in the same order. {@link #BUTTON_RESET} sits well above them,
 * and anything from {@link #DURATION_ID_BASE} up is a duration value rather than a button (see
 * {@code LinkDeviceMenu.SIGNAL_LENGTH_ID_BASE}, the same trick).
 */
public abstract class AbstractDeviceModeMenu<D extends BlockEntity> extends AbstractContainerMenu {

    /**
     * Restores every one of {@link #durations()} to its default. Parked well clear of the mode
     * ids, which run {@code 0..n-1} - it used to be 2, which the siren's third mode collided with.
     */
    public static final int BUTTON_RESET = 1_000;
    /**
     * Duration values are sent as {@code DURATION_ID_BASE + index * DURATION_ID_STRIDE + ticks},
     * where {@code index} is the setting's position in {@link #durations()}.
     */
    public static final int DURATION_ID_BASE = 100_000;
    /** One setting's id range - wider than {@link #MAX_DURATION_TICKS}, so two can't overlap. */
    public static final int DURATION_ID_STRIDE = 100_000;
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

    /**
     * One tick-valued setting: how to read it, how to set it, and how to restore its default for
     * {@link #BUTTON_RESET}.
     */
    protected record DurationSetting(IntSupplier get, IntConsumer set, Runnable reset) {
    }

    /**
     * This device's duration settings, in the same order as
     * {@link AbstractDeviceModeScreen#durations()} - a setting's index there picks its id range
     * here, see the class doc.
     */
    protected abstract List<DurationSetting> durations();

    // ---- Button handling ----

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= DURATION_ID_BASE) {
            return applyDuration(id - DURATION_ID_BASE);
        }
        if (id == BUTTON_RESET) {
            durations().forEach(setting -> setting.reset().run());
            return true;
        }
        List<Runnable> setters = modeSetters();
        if (id >= 0 && id < setters.size()) {
            setters.get(id).run();
            return true;
        }
        return false;
    }

    /** Splits a duration payload back into "which setting" and "how many ticks" - see the class doc. */
    private boolean applyDuration(int payload) {
        List<DurationSetting> settings = durations();
        int index = payload / DURATION_ID_STRIDE;
        if (index >= settings.size()) {
            return false;
        }
        DurationSetting setting = settings.get(index);
        // 0 is a legitimate value (instant cutoff) - not clamped up to 1, same as
        // LinkDeviceMenu's signal length
        int ticks = Math.clamp(payload % DURATION_ID_STRIDE, 0, MAX_DURATION_TICKS);
        if (ticks != setting.get().getAsInt()) {
            setting.set().accept(ticks);
            DKSounds.valueConfirm(device.getLevel(), device.getBlockPos());
        }
        return true;
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
