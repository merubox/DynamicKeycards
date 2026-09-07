package com.mbx.dynamickeycards.menu;

import com.mbx.dynamickeycards.DKSounds;
import com.mbx.dynamickeycards.block.AdvancedSensorBlockEntity;
import com.mbx.dynamickeycards.block.BoundReaderMode;
import com.mbx.dynamickeycards.block.LinkDeviceBlockEntity;
import com.mbx.dynamickeycards.block.SignalMode;
import com.mbx.dynamickeycards.compat.create.CreateAvailability;
import com.mbx.dynamickeycards.registry.DKMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Wrench-opened config UI shared by every {@link LinkDeviceBlockEntity}: two ghost frequency slots (indices 0 and 1), used as a Redstone Link
 * network key, plus the mode toggle / reset / signal-length controls. Slots 2-37 are the
 * player's own inventory (3x9 + hotbar), same layout vanilla containers use.
 *
 * <p>Slot/button coordinates match {@code LinkDeviceScreen} and its 184x99 background canvas.
 */
public class LinkDeviceMenu extends AbstractContainerMenu {

    public static final int GHOST_SLOT_COUNT = 2;

    /**
     * The two ghost frequency slots only mean anything as a Create Redstone Link key - see
     * {@code CreateAvailability}'s own doc for why {@code isLoaded()} is checked once here (server
     * and client both construct this menu independently from their own {@code fromNetwork}/direct
     * paths, so each side computes this itself rather than one side telling the other). {@code 0}
     * when Create isn't installed: the constructor then skips adding the two ghost {@link Slot}s
     * at all, so there's nothing in {@code menu.slots} for the screen to render or the player to
     * click on - not just locked, genuinely absent - and the player-inventory slots added right
     * after simply start two indices earlier instead, with no other change needed anywhere else in
     * this class.
     */
    private final int uiGhostSlotCount;

    /** {@link #clickMenuButton} ids, sent from the screen's mode/reset buttons. */
    public static final int BUTTON_NORMAL_MODE = 0;
    public static final int BUTTON_LINK_MODE = 1;
    public static final int BUTTON_SIMULTANEOUS_MODE = 2;
    public static final int BUTTON_RESET = 3;
    /**
     * Signal length values (in ticks) are sent as {@code SIGNAL_LENGTH_ID_BASE + ticks} —
     * an ordinary {@code int} id, same mechanism as the other buttons, just offset high
     * enough (max tick count is 72000, see {@code DKConfig}) that it can never collide with
     * the small fixed ids above.
     */
    public static final int SIGNAL_LENGTH_ID_BASE = 10_000;

    private final LinkDeviceBlockEntity device;

    public LinkDeviceMenu(int containerId, Inventory playerInventory, LinkDeviceBlockEntity device) {
        super(DKMenuTypes.LINK_DEVICE.get(), containerId);
        this.device = device;

        this.uiGhostSlotCount = CreateAvailability.isLoaded() ? GHOST_SLOT_COUNT : 0;
        if (uiGhostSlotCount > 0) {
            Container ghostContainer = new GhostFrequencyContainer(device);
            // matches the frequency #1 (red) / #2 (blue) slots in the screen's background art
            this.addSlot(ghostSlot(ghostContainer, 0, 80, 25));
            this.addSlot(ghostSlot(ghostContainer, 1, 80, 43));
        }

        // matches LinkDeviceScreen's player-inventory panel: panel sits flush at local
        // x=0 with the main panel, slots are +8/+18 into it from there
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 121 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 179));
        }
    }

    /** A single-item ghost slot: never gives up what it holds, only ever mirrors it (see {@link #clicked}). */
    private static Slot ghostSlot(Container container, int index, int x, int y) {
        return new Slot(container, index, x, y) {
            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
    }

    public LinkDeviceBlockEntity getDevice() {
        return device;
    }

    /**
     * Handles the screen's normal-mode / link-mode / mixed-mode / reset buttons (sent via
     * vanilla {@code AbstractContainerMenu} button-click networking, same mechanism as e.g. the
     * enchanting table — no custom packet needed).
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= SIGNAL_LENGTH_ID_BASE) {
            return applySignalLength(id - SIGNAL_LENGTH_ID_BASE);
        }
        // a sensor bound to a reader repurposes the three mode buttons to its own BoundReaderMode
        // set instead of SignalMode - see AdvancedSensorBlockEntity's own doc for why
        if (device instanceof AdvancedSensorBlockEntity sensor && sensor.getBoundReader() != null) {
            return boundSensorButton(sensor, id);
        }
        return deviceButton(id);
    }

    /** Always handled, even when the value is unchanged - the button did belong to this menu. */
    private boolean applySignalLength(int ticks) {
        // 0 is a legitimate value (e.g. a sensor released the instant its hold delay lapses,
        // rather than lingering) - not clamped up to 1
        int clamped = Math.clamp(ticks, 0, 72000);
        if (clamped != device.getSignalLength()) {
            device.setSignalLength(clamped);
            DKSounds.valueConfirm(device.getLevel(), device.getBlockPos());
        }
        return true;
    }

    /**
     * Reset here clears the signal length only: a sensor bound to a reader doesn't own its
     * frequency either, so there'd be nothing of its own to clear.
     */
    private boolean boundSensorButton(AdvancedSensorBlockEntity sensor, int id) {
        switch (id) {
            case BUTTON_NORMAL_MODE -> sensor.setBoundReaderMode(BoundReaderMode.SENSOR_CENTRIC_SIMULTANEOUS);
            case BUTTON_LINK_MODE -> sensor.setBoundReaderMode(BoundReaderMode.READER_ONLY);
            case BUTTON_SIMULTANEOUS_MODE -> sensor.setBoundReaderMode(BoundReaderMode.SIMULTANEOUS);
            case BUTTON_RESET -> device.clearSignalLength();
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean deviceButton(int id) {
        switch (id) {
            case BUTTON_NORMAL_MODE -> setSignalMode(SignalMode.NORMAL);
            case BUTTON_LINK_MODE -> setSignalMode(SignalMode.LINK);
            case BUTTON_SIMULTANEOUS_MODE -> setSignalMode(SignalMode.SIMULTANEOUS);
            case BUTTON_RESET -> reset();
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * A sensor bound to *another sensor* already has its actual mode owned by that sensor - the
     * mode buttons are locked out while that's the case, see
     * {@link LinkDeviceBlockEntity#isLinkModeEditable}.
     */
    private void setSignalMode(SignalMode mode) {
        if (device.isLinkModeEditable()) {
            device.setSignalMode(mode);
        }
    }

    /** The signal length is always this device's own, so it resets even when the frequency can't. */
    private void reset() {
        if (device.isFrequencyEditable()) {
            device.setFrequencySlot(0, ItemStack.EMPTY);
            device.setFrequencySlot(1, ItemStack.EMPTY);
        }
        device.clearSignalLength();
    }

    public static LinkDeviceMenu fromNetwork(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        BlockPos pos = extraData.readBlockPos();
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
        if (!(blockEntity instanceof LinkDeviceBlockEntity device)) {
            throw new IllegalStateException("No link device at " + pos);
        }
        return new LinkDeviceMenu(containerId, playerInventory, device);
    }

    /**
     * Ghost-slot click handling (indices 0/1): remembers a count-1 copy of whatever's carried
     * without ever touching the carried stack itself. Everything else falls through to normal
     * vanilla behavior.
     */
    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (slotId < 0 || slotId >= uiGhostSlotCount) {
            super.clicked(slotId, dragType, clickType, player);
            return;
        }
        if (clickType == ClickType.THROW || !device.isFrequencyEditable()) {
            return;
        }
        ItemStack carried = getCarried();
        device.setFrequencySlot(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
        this.getSlot(slotId).setChanged();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < uiGhostSlotCount) {
            // nothing is ever actually held in a ghost slot, so there's nothing to move out
            return ItemStack.EMPTY;
        }
        ItemStack clicked = this.getSlot(index).getItem();
        if (!clicked.isEmpty() && uiGhostSlotCount > 0 && device.isFrequencyEditable()) {
            for (int i = 0; i < uiGhostSlotCount; i++) {
                if (device.getFrequencySlot(i).isEmpty()) {
                    device.setFrequencySlot(i, clicked.copyWithCount(1));
                    this.getSlot(i).setChanged();
                    break;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        Level level = device.getLevel();
        BlockPos pos = device.getBlockPos();
        return level != null && level.getBlockEntity(pos) == device
                && Container.stillValidBlockEntity((BlockEntity) device, player);
    }

    private static class GhostFrequencyContainer implements Container {
        private final LinkDeviceBlockEntity device;

        GhostFrequencyContainer(LinkDeviceBlockEntity device) {
            this.device = device;
        }

        @Override
        public int getContainerSize() {
            return GHOST_SLOT_COUNT;
        }

        @Override
        public boolean isEmpty() {
            return device.getFrequencySlot(0).isEmpty() && device.getFrequencySlot(1).isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return device.getFrequencySlot(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            device.setFrequencySlot(slot, stack);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            device.setFrequencySlot(0, ItemStack.EMPTY);
            device.setFrequencySlot(1, ItemStack.EMPTY);
        }
    }
}
