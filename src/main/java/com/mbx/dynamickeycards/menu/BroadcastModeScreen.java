package com.mbx.dynamickeycards.menu;

import com.mbx.dynamickeycards.DKTooltips;
import com.mbx.dynamickeycards.DynamicKeycards;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Card reader "broadcast mode" screen: background + two ghost frequency slots from
 * {@link BroadcastModeMenu}, plus four buttons (normal mode / broadcast mode / reset / confirm)
 * drawn from {@code broadcast_widgets.png} (button-box states) and {@code broadcast_icons.png}
 * (icon glyphs).
 *
 * <p>Main panel is 184x99 with ghost slots at (80,25)/(80,43) and buttons on one row at
 * y=75 — normal (7) and broadcast (25) flush against each other, reset (122), confirm (151) —
 * then a separate player-inventory panel ({@code broadcast_player_inventory.png}) below it at
 * local x=30, y = BG_HEIGHT+4; slots at {@code invX+8+col*18, invY+18+row*18} (hotbar
 * {@code invY+76}) — see {@link BroadcastModeMenu} for the matching slot coordinates.
 * {@code imageHeight} includes the inventory panel's height too, so the whole window (not just
 * the main panel) is what gets centered on screen. {@code leftPos}/{@code topPos} get a small
 * (-11, 5) nudge applied after centering to land at the intended on-screen position.
 *
 * <p>To the right of the arrow, a scaled-up render of the reader's own item (whichever of the
 * five reader blocks this menu belongs to) sits next to the arrow as a block preview. Button
 * tooltips follow this mod's existing {@link DKTooltips#summary} convention (name only,
 * "Hold [Shift]" hint, full description while Shift is held) instead of vanilla's plain
 * {@code Tooltip}, and the title is drawn manually in {@code TITLE_COLOR} rather than
 * vanilla's default.
 */
public class BroadcastModeScreen extends AbstractContainerScreen<BroadcastModeMenu> {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/broadcast_mode.png");
    private static final ResourceLocation WIDGETS =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/broadcast_widgets.png");
    private static final ResourceLocation ICONS =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/broadcast_icons.png");
    private static final ResourceLocation PLAYER_INVENTORY =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/broadcast_player_inventory.png");

    private static final int BG_WIDTH = 184;
    private static final int BG_HEIGHT = 99;
    private static final int BUTTON_Y = 75;
    private static final int PLAYER_INV_WIDTH = 176;
    private static final int PLAYER_INV_HEIGHT = 108;
    private static final int TITLE_COLOR = 0x303030;

    private ModeButton normalModeButton;
    private ModeButton broadcastModeButton;

    public BroadcastModeScreen(BroadcastModeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = BG_WIDTH;
        // whole window height (main panel + gap + inventory panel), so the screen centers
        // correctly on the player's actual screen instead of sitting low
        this.imageHeight = BG_HEIGHT + 4 + PLAYER_INV_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        // nudges the window by (4, 5) after centering to land at the intended position
        this.leftPos += 4;
        this.topPos += 5;

        normalModeButton = addRenderableWidget(new ModeButton(leftPos + 7, topPos + BUTTON_Y, ModeButton.ICON_NORMAL,
                "normal_mode", () -> sendButtonClick(BroadcastModeMenu.BUTTON_NORMAL_MODE)));

        broadcastModeButton = addRenderableWidget(new ModeButton(leftPos + 25, topPos + BUTTON_Y, ModeButton.ICON_BROADCAST,
                "broadcast_mode", () -> sendButtonClick(BroadcastModeMenu.BUTTON_BROADCAST_MODE)));
        // the antenna glyph itself sits 1px left of where it should within its icon cell -
        // button box and hitbox are untouched, only the icon draw position moves
        broadcastModeButton.iconOffsetX = 1;

        // no tooltip on these two - icon-obvious enough, so titleKey is unused (null)
        addRenderableWidget(new ModeButton(leftPos + 122, topPos + BUTTON_Y, ModeButton.ICON_RESET,
                null, () -> sendButtonClick(BroadcastModeMenu.BUTTON_RESET)));

        addRenderableWidget(new ModeButton(leftPos + 151, topPos + BUTTON_Y, ModeButton.ICON_CONFIRM,
                null, () -> minecraft.player.closeContainer()));

        updateModeIndicators();
    }

    private void sendButtonClick(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateModeIndicators();
    }

    private void updateModeIndicators() {
        boolean broadcasting = menu.getReader().isBroadcastEnabled();
        normalModeButton.green = !broadcasting;
        broadcastModeButton.green = broadcasting;
    }

    /** Suppresses vanilla's default title/inventory labels - both are drawn manually instead. */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, BG_WIDTH, BG_HEIGHT, BG_WIDTH, BG_HEIGHT);

        // centered in the header bar using (width-8)/2 - textWidth/2, not a plain midpoint,
        // to account for the arrow decoration
        int titleX = (BG_WIDTH - 8) / 2 - font.width(title) / 2;
        graphics.drawString(font, title, leftPos + titleX, topPos + 4, TITLE_COLOR, false);

        int invX = leftPos;
        int invY = topPos + BG_HEIGHT + 4;
        graphics.blit(PLAYER_INVENTORY, invX, invY, 0, 0, PLAYER_INV_WIDTH, PLAYER_INV_HEIGHT,
                PLAYER_INV_WIDTH, PLAYER_INV_HEIGHT);
        graphics.drawString(font, playerInventoryTitle, invX + 8, invY + 6, 0x404040, false);

        renderDeviceIcon(graphics);
    }

    /** Screen-space bounds of {@link #renderDeviceIcon}, in pixels (16x16 art scaled 4x = 64x64). */
    private static final int ICON_SIZE = 64;

    /**
     * A scaled-up icon of whichever reader block this screen belongs to, rendered next to the
     * arrow (all five reader types share this one screen, so the icon has to follow the
     * instance).
     */
    private void renderDeviceIcon(GuiGraphics graphics) {
        ItemStack stack = new ItemStack(menu.getReader().getBlockState().getBlock().asItem());
        int x = getIconAreaX();
        int y = getIconAreaY();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 100);
        graphics.pose().scale(4, 4, 4);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    private int getIconAreaX() {
        return leftPos + BG_WIDTH + 8;
    }

    private int getIconAreaY() {
        return topPos + BG_HEIGHT - 52;
    }

    /**
     * Screen-space bounds of the device icon (x, y, width, height), so an EMI plugin can
     * register it as an exclusion area - EMI has no idea this region is occupied otherwise and
     * happily draws its own item panel stacks right on top of it.
     */
    public int[] getDeviceIconScreenBounds() {
        return new int[] {getIconAreaX(), getIconAreaY(), ICON_SIZE, ICON_SIZE};
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderButtonTooltip(graphics, normalModeButton, mouseX, mouseY);
        renderButtonTooltip(graphics, broadcastModeButton, mouseX, mouseY);
        renderEmptySlotTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Same "name + Hold [Shift] for Summary" convention as the mod's item tooltips. Reset/confirm
     * are icon-obvious enough that they get no tooltip at all (not even a name).
     * Checks a live {@code isMouseOver} rather than {@code isHoveredOrFocused} - focus lingers
     * after a click, which was leaving the tooltip stuck on screen after pressing a button.
     */
    private void renderButtonTooltip(GuiGraphics graphics, ModeButton button, int mouseX, int mouseY) {
        if (!button.isMouseOver(mouseX, mouseY)) {
            return;
        }
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("dynamickeycards.card_reader." + button.titleKey));
        DKTooltips.summary(tooltip, button.titleKey + "1");
        graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    /** An empty frequency slot shows its own name (Frequency #1/#2) as a hover tooltip. */
    private void renderEmptySlotTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredSlot == null || hoveredSlot.hasItem()) {
            return;
        }
        String key;
        if (hoveredSlot == menu.getSlot(0)) {
            key = "broadcast_slot1";
        } else if (hoveredSlot == menu.getSlot(1)) {
            key = "broadcast_slot2";
        } else {
            return;
        }
        graphics.renderTooltip(font, Component.translatable("dynamickeycards.card_reader." + key), mouseX, mouseY);
    }

    /** Button box (gray/hover/green/down, from {@link #WIDGETS}) + a 16x16 icon from {@link #ICONS}. */
    private static class ModeButton extends AbstractButton {

        static final int ICON_NORMAL = 0;
        static final int ICON_BROADCAST = 16;
        static final int ICON_RESET = 32;
        static final int ICON_CONFIRM = 48;

        private final int iconU;
        private final String titleKey;
        private final Runnable onPress;
        boolean green;
        int iconOffsetX;
        private boolean pressed;

        ModeButton(int x, int y, int iconU, String titleKey, Runnable onPress) {
            super(x, y, 18, 18, Component.empty());
            this.iconU = iconU;
            this.titleKey = titleKey;
            this.onPress = onPress;
        }

        @Override
        public void onPress() {
            onPress.run();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            boolean handled = super.mouseClicked(mouseX, mouseY, button);
            if (handled) {
                pressed = true;
            }
            return handled;
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            pressed = false;
            super.onRelease(mouseX, mouseY);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int stateU;
            if (isHovered && pressed) {
                stateU = 54;
            } else if (isHovered) {
                stateU = 18;
            } else if (green) {
                stateU = 36;
            } else {
                stateU = 0;
            }
            graphics.blit(WIDGETS, getX(), getY(), stateU, 0, 18, 18, 72, 18);
            graphics.blit(ICONS, getX() + 1 + iconOffsetX, getY() + 1, iconU, 0, 16, 16, 64, 16);
        }
    }
}
