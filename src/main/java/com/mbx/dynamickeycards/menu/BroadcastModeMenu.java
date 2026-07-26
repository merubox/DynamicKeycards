package com.mbx.dynamickeycards.menu;

import com.mbx.dynamickeycards.block.CardReaderBlockEntity;
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
 * Card reader "broadcast mode": two ghost frequency slots (indices 0 and 1), used as a
 * Redstone Link network key and backed by {@link CardReaderBlockEntity#getFrequencySlot}.
 * Slots 2-37 are the player's own inventory (3x9 + hotbar), same layout vanilla containers
 * use.
 *
 * <p>Slot/button coordinates match {@code BroadcastModeScreen} and its 184x99 background
 * canvas.
 */
public class BroadcastModeMenu extends AbstractContainerMenu {

    public static final int GHOST_SLOT_COUNT = 2;

    /** {@link #clickMenuButton} ids, sent from the screen's mode/reset buttons. */
    public static final int BUTTON_NORMAL_MODE = 0;
    public static final int BUTTON_BROADCAST_MODE = 1;
    public static final int BUTTON_RESET = 2;

    private final CardReaderBlockEntity reader;

    public BroadcastModeMenu(int containerId, Inventory playerInventory, CardReaderBlockEntity reader) {
        super(DKMenuTypes.BROADCAST_MODE.get(), containerId);
        this.reader = reader;

        Container ghostContainer = new GhostFrequencyContainer(reader);
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

        // matches BroadcastModeScreen's player-inventory panel: panel sits flush at local
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

    public CardReaderBlockEntity getReader() {
        return reader;
    }

    /**
     * Handles the screen's normal-mode / broadcast-mode / reset buttons (sent via vanilla
     * {@code AbstractContainerMenu} button-click networking, same mechanism as e.g. the
     * enchanting table — no custom packet needed).
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case BUTTON_NORMAL_MODE -> reader.setBroadcastEnabled(false);
            case BUTTON_BROADCAST_MODE -> reader.setBroadcastEnabled(true);
            case BUTTON_RESET -> {
                reader.setFrequencySlot(0, ItemStack.EMPTY);
                reader.setFrequencySlot(1, ItemStack.EMPTY);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    public static BroadcastModeMenu fromNetwork(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        BlockPos pos = extraData.readBlockPos();
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
        if (!(blockEntity instanceof CardReaderBlockEntity reader)) {
            throw new IllegalStateException("No card reader at " + pos);
        }
        return new BroadcastModeMenu(containerId, playerInventory, reader);
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
        if (clickType == ClickType.THROW) {
            return;
        }
        ItemStack carried = getCarried();
        reader.setFrequencySlot(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
        this.getSlot(slotId).setChanged();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < GHOST_SLOT_COUNT) {
            // nothing is ever actually held in a ghost slot, so there's nothing to move out
            return ItemStack.EMPTY;
        }
        ItemStack clicked = this.getSlot(index).getItem();
        if (!clicked.isEmpty()) {
            for (int i = 0; i < GHOST_SLOT_COUNT; i++) {
                if (reader.getFrequencySlot(i).isEmpty()) {
                    reader.setFrequencySlot(i, clicked.copyWithCount(1));
                    this.getSlot(i).setChanged();
                    break;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        Level level = reader.getLevel();
        BlockPos pos = reader.getBlockPos();
        return level != null && level.getBlockEntity(pos) == reader
                && Container.stillValidBlockEntity(reader, player);
    }

    private static class GhostFrequencyContainer implements Container {
        private final CardReaderBlockEntity reader;

        GhostFrequencyContainer(CardReaderBlockEntity reader) {
            this.reader = reader;
        }

        @Override
        public int getContainerSize() {
            return GHOST_SLOT_COUNT;
        }

        @Override
        public boolean isEmpty() {
            return reader.getFrequencySlot(0).isEmpty() && reader.getFrequencySlot(1).isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return reader.getFrequencySlot(slot);
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
            reader.setFrequencySlot(slot, stack);
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
            reader.setFrequencySlot(0, ItemStack.EMPTY);
            reader.setFrequencySlot(1, ItemStack.EMPTY);
        }
    }
}
