package com.mbx.dynamickeycards.menu;

import com.mbx.dynamickeycards.block.LinkDeviceBlockEntity;
import com.mbx.dynamickeycards.block.SignalMode;
import com.mbx.dynamickeycards.registry.DKMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Wrench-opened config UI shared by every {@link LinkDeviceBlockEntity} (the card reader, and
 * both motion sensors): two ghost frequency slots (indices 0 and 1), used as a Redstone Link
 * network key, plus the mode toggle / reset / signal-length controls. Slots 2-37 are the
 * player's own inventory (3x9 + hotbar), same layout vanilla containers use.
 *
 * <p>Slot/button coordinates match {@code LinkDeviceScreen} and its 184x99 background canvas.
 */
public class LinkDeviceMenu extends AbstractContainerMenu {

    public static final int GHOST_SLOT_COUNT = 2;

    /** {@link #clickMenuButton} ids, sent from the screen's mode/reset buttons. */
    public static final int BUTTON_NORMAL_MODE = 0;
    public static final int BUTTON_LINK_MODE = 1;
    public static final int BUTTON_MIXED_MODE = 2;
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

        Container ghostContainer = new GhostFrequencyContainer(device);
        // matches the frequency #1 (red) / #2 (blue) slots in the screen's background art
        this.addSlot(new Slot(ghostContainer, 0, 80, 25) {
            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        this.addSlot(new Slot(ghostContainer, 1, 80, 43) {
            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

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
            // 0 is a legitimate value (e.g. a sensor released the instant its hold delay lapses,
            // rather than lingering) - not clamped up to 1
            int ticks = Math.clamp(id - SIGNAL_LENGTH_ID_BASE, 0, 72000);
            if (ticks != device.getSignalLength()) {
                device.setSignalLength(ticks);
                playPulseConfirmSound();
            }
            return true;
        }
        switch (id) {
            // a bound advanced sensor already has its actual mode/frequency owned by the reader
            // it's bound to - these three are locked out while that's the case, see
            // LinkDeviceBlockEntity#isLinkModeEditable
            case BUTTON_NORMAL_MODE -> {
                if (device.isLinkModeEditable()) {
                    device.setSignalMode(SignalMode.NORMAL);
                }
            }
            case BUTTON_LINK_MODE -> {
                if (device.isLinkModeEditable()) {
                    device.setSignalMode(SignalMode.LINK);
                }
            }
            case BUTTON_MIXED_MODE -> {
                if (device.isLinkModeEditable()) {
                    device.setSignalMode(SignalMode.MIXED);
                }
            }
            case BUTTON_RESET -> {
                if (device.isLinkModeEditable()) {
                    device.setFrequencySlot(0, ItemStack.EMPTY);
                    device.setFrequencySlot(1, ItemStack.EMPTY);
                }
                device.clearSignalLength();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * Two plain vanilla sounds (not one of this mod's own {@code DKSounds} tones) layered
     * quietly on top of each other - a sharp high click plus a very faint xylophone note.
     * Deliberately not folded into {@code DKSounds}: that class documents this mod's own
     * five-tone feedback vocabulary, and this pairing exists only to match what players
     * already hear from value-adjustment scales elsewhere, independent of that vocabulary.
     */
    private void playPulseConfirmSound() {
        Level level = device.getLevel();
        BlockPos pos = device.getBlockPos();
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.25f, 2f);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), SoundSource.BLOCKS, 0.03f, 1.125f);
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
        if (slotId < 0 || slotId >= GHOST_SLOT_COUNT) {
            super.clicked(slotId, dragType, clickType, player);
            return;
        }
        if (clickType == ClickType.THROW || !device.isLinkModeEditable()) {
            return;
        }
        ItemStack carried = getCarried();
        device.setFrequencySlot(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
        this.getSlot(slotId).setChanged();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < GHOST_SLOT_COUNT) {
            // nothing is ever actually held in a ghost slot, so there's nothing to move out
            return ItemStack.EMPTY;
        }
        ItemStack clicked = this.getSlot(index).getItem();
        if (!clicked.isEmpty() && device.isLinkModeEditable()) {
            for (int i = 0; i < GHOST_SLOT_COUNT; i++) {
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
