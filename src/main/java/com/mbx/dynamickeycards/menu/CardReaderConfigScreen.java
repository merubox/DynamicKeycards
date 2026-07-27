package com.mbx.dynamickeycards.menu;

import com.mbx.dynamickeycards.DKTooltips;
import com.mbx.dynamickeycards.DynamicKeycards;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Card reader's wrench-opened config screen: background + two ghost frequency slots from
 * {@link CardReaderConfigMenu}, plus four buttons (normal mode / link mode / reset / confirm)
 * drawn from {@code broadcast_widgets.png} (button-box states) and {@code broadcast_icons.png}
 * (icon glyphs).
 *
 * <p>Main panel is 184x99 with ghost slots at (80,25)/(80,43) and buttons on one row at
 * y=75 — normal (7) and link (25) flush against each other, reset (122), confirm (151) —
 * then a separate player-inventory panel ({@code broadcast_player_inventory.png}) below it at
 * local x=30, y = BG_HEIGHT+4; slots at {@code invX+8+col*18, invY+18+row*18} (hotbar
 * {@code invY+76}) — see {@link CardReaderConfigMenu} for the matching slot coordinates.
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
 *
 * <p>The number box on the button row (local 70,75 - 105,92) shows the reader's current pulse
 * length. Holding right-click on it for a few ticks opens a full-screen value-adjustment
 * overlay: three horizontal scales (ticks / seconds / minutes) with milestone marks every 10
 * units; moving the mouse (or scrolling) picks the row closest to the cursor's height and the
 * value closest to its position along that row, and releasing right-click confirms whatever is
 * currently highlighted and sends it to the server. Shift snaps movement to whole milestones.
 */
public class CardReaderConfigScreen extends AbstractContainerScreen<CardReaderConfigMenu> {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/broadcast_mode.png");
    private static final ResourceLocation WIDGETS =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/broadcast_widgets.png");
    private static final ResourceLocation ICONS =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/broadcast_icons.png");
    private static final ResourceLocation PLAYER_INVENTORY =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/broadcast_player_inventory.png");
    private static final ResourceLocation PULSE_TEX =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/pulse_length.png");

    private static final int BG_WIDTH = 184;
    private static final int BG_HEIGHT = 99;
    private static final int BUTTON_Y = 75;
    private static final int PLAYER_INV_WIDTH = 176;
    private static final int PLAYER_INV_HEIGHT = 108;
    private static final int TITLE_COLOR = 0x303030;

    private static final int PULSE_BOX_X = 70;
    private static final int PULSE_BOX_W = 36;
    private static final int PULSE_BOX_H = 18;
    private static final int PULSE_HOLD_OPEN_TICKS = 5;
    private static final int PULSE_MAX_VALUE = 60;
    private static final int PULSE_MILESTONE_INTERVAL = 10;
    private static final int PULSE_BAR_SCALE = 2;
    private static final int PULSE_MILESTONE_SIZE = 4;
    private static final int PULSE_ROW_HEIGHT = 11;
    private static final int[] PULSE_ROW_MULTIPLIER = {1, 20, 1200};
    private static final String[] PULSE_ROW_KEYS = {"ticks", "seconds", "minutes"};
    /**
     * Not one of our own sounds - looked up by id at runtime, so this only ever plays when
     * Create happens to be installed (the lookup is simply absent otherwise). No Create class
     * is referenced anywhere for this, so it carries none of the usual optional-dependency risk.
     */
    private static final ResourceLocation SCROLL_SOUND_ID =
            ResourceLocation.fromNamespaceAndPath("create", "scroll_value");

    /** A (u, v, width, height) region of {@link #PULSE_TEX} (256x256). */
    private record Tex(int u, int v, int w, int h) {
    }

    private static final Tex TEX_MILESTONE = new Tex(0, 0, 7, 8);
    private static final Tex TEX_BAR = new Tex(7, 0, 249, 8);
    private static final Tex TEX_BAR_BG = new Tex(75, 9, 1, 1);
    private static final Tex TEX_OUTER_BG = new Tex(80, 9, 1, 1);
    private static final Tex TEX_CURSOR_LEFT = new Tex(0, 9, 3, 14);
    private static final Tex TEX_CURSOR = new Tex(4, 9, 56, 14);
    private static final Tex TEX_CURSOR_RIGHT = new Tex(61, 9, 3, 14);
    private static final Tex TEX_LABEL_BG = new Tex(0, 31, 161, 11);
    private static final Tex TEX_FRAME_TL = new Tex(65, 9, 4, 4);
    private static final Tex TEX_FRAME_TR = new Tex(70, 9, 4, 4);
    private static final Tex TEX_FRAME_BL = new Tex(65, 19, 4, 4);
    private static final Tex TEX_FRAME_BR = new Tex(70, 19, 4, 4);
    private static final Tex TEX_FRAME_LEFT = new Tex(65, 14, 3, 4);
    private static final Tex TEX_FRAME_RIGHT = new Tex(71, 14, 3, 4);
    private static final Tex TEX_FRAME_TOP = new Tex(0, 24, 256, 3);
    private static final Tex TEX_FRAME_BOTTOM = new Tex(0, 27, 256, 3);
    private static final Tex TEX_LABEL_BG_SELECTED = new Tex(0, 64, 161, 11);
    private static final Tex TEX_BAR_SELECTED = new Tex(0, 76, 249, 8);
    private static final Tex TEX_MILESTONE_SELECTED = new Tex(0, 86, 7, 8);

    private ModeButton normalModeButton;
    private ModeButton broadcastModeButton;

    private int pulseBoxHeldTicks = -1;
    private boolean pulseLengthPopupOpen;
    private int pulseLabelWidth;
    private int pulseValueBarWidth;
    private int pulsePopupX;
    private int pulsePopupY;
    private int pulseBoxX;
    private int pulseBoxY;
    private int pulseBoxW;
    private int pulseBoxH;
    private int pulseRowsY;
    private int pulseRowsHeight;
    private int pulseMilestoneCount;
    private int pulseHintY;
    private int pulseHoverRow;
    private int pulseHoverValue;
    /**
     * At most one scroll sound per game tick - mouseMoved can fire many times within a single
     * tick during a fast sweep, and without this the sound would stack/overlap and fall out of
     * sync with the value actually landed on instead of playing once, cleanly, per tick.
     */
    private int pulseSoundCooldown;

    public CardReaderConfigScreen(CardReaderConfigMenu menu, Inventory playerInventory, Component title) {
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
                "normal_mode", () -> sendButtonClick(CardReaderConfigMenu.BUTTON_NORMAL_MODE)));

        broadcastModeButton = addRenderableWidget(new ModeButton(leftPos + 25, topPos + BUTTON_Y, ModeButton.ICON_BROADCAST,
                "broadcast_mode", () -> sendButtonClick(CardReaderConfigMenu.BUTTON_BROADCAST_MODE)));
        // the antenna glyph itself sits 1px left of where it should within its icon cell -
        // button box and hitbox are untouched, only the icon draw position moves
        broadcastModeButton.iconOffsetX = 1;

        // no tooltip on these two - icon-obvious enough, so titleKey is unused (null)
        addRenderableWidget(new ModeButton(leftPos + 122, topPos + BUTTON_Y, ModeButton.ICON_RESET,
                null, () -> sendButtonClick(CardReaderConfigMenu.BUTTON_RESET)));

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
        tickPulseBoxHold();
        if (pulseSoundCooldown > 0) {
            pulseSoundCooldown--;
        }
    }

    private void updateModeIndicators() {
        boolean broadcasting = menu.getReader().isBroadcastEnabled();
        normalModeButton.green = !broadcasting;
        broadcastModeButton.green = broadcasting;
    }

    private void tickPulseBoxHold() {
        if (pulseLengthPopupOpen || pulseBoxHeldTicks < 0) {
            return;
        }
        if (pulseBoxHeldTicks++ >= PULSE_HOLD_OPEN_TICKS) {
            openPulseLengthPopup();
            pulseBoxHeldTicks = -1;
        }
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
        renderPulseLengthBox(graphics);
    }

    /**
     * Number readout in the button row showing the reader's current pulse length. The box
     * itself is already part of {@link #BACKGROUND} - only the text is drawn here.
     */
    private void renderPulseLengthBox(GuiGraphics graphics) {
        int x = leftPos + PULSE_BOX_X;
        int y = topPos + BUTTON_Y;
        int ticks = menu.getReader().getPulseLength();
        String text = formatTicksCompact(ticks);
        graphics.drawCenteredString(font, text, x + PULSE_BOX_W / 2, y + (PULSE_BOX_H - 8) / 2, 0xFFFFFF);
    }

    private static String formatTicksCompact(int ticks) {
        if (ticks % 1200 == 0) {
            return (ticks / 1200) + "m";
        }
        if (ticks % 20 == 0) {
            return (ticks / 20) + "s";
        }
        return ticks + "t";
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

    /**
     * Non-null only while the pulse length popup is open, in which case it covers the whole
     * screen. This has to be the full screen, not just the popup's own box: EMI's tooltip is
     * gated entirely on whether {@code EmiScreenBase.of(screen)} considers this screen "active",
     * which for a screen with real container slots is unconditional (confirmed against EMI's own
     * source) and fires through a NeoForge event that isn't cancellable - there's no finer-grained
     * official way to suppress just the tooltip while leaving the rest of EMI's panel alone.
     */
    public int[] getPulseLengthPopupScreenBounds() {
        return pulseLengthPopupOpen ? new int[] {0, 0, this.width, this.height} : null;
    }

    /**
     * Vanilla (and anything else hooking this same method, e.g. a recipe-viewer mod's own
     * ingredient tooltip) renders tooltips through a deferred queue that always draws on top of
     * everything else in the frame, regardless of the Z we push our own content to - so the only
     * way to keep them from poking through the pulse length popup is to stop them from being
     * queued in the first place, not to try to out-draw them.
     */
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (pulseLengthPopupOpen) {
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderButtonTooltip(graphics, normalModeButton, mouseX, mouseY);
        renderButtonTooltip(graphics, broadcastModeButton, mouseX, mouseY);
        renderEmptySlotTooltip(graphics, mouseX, mouseY);
        renderPulseBoxTooltip(graphics, mouseX, mouseY);
        if (pulseLengthPopupOpen) {
            renderPulseLengthPopup(graphics);
        }
    }

    /**
     * Same "name + Hold [Shift] for Summary" convention as the mod's item tooltips. Reset/confirm
     * are icon-obvious enough that they get no tooltip at all (not even a name).
     * Checks a live {@code isMouseOver} rather than {@code isHoveredOrFocused} - focus lingers
     * after a click, which was leaving the tooltip stuck on screen after pressing a button.
     */
    private void renderButtonTooltip(GuiGraphics graphics, ModeButton button, int mouseX, int mouseY) {
        if (pulseLengthPopupOpen || !button.isMouseOver(mouseX, mouseY)) {
            return;
        }
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("dynamickeycards.card_reader." + button.titleKey));
        DKTooltips.summary(tooltip, button.titleKey + "1");
        graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    /** An empty frequency slot shows its own name (Frequency #1/#2) as a hover tooltip. */
    private void renderEmptySlotTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (pulseLengthPopupOpen || hoveredSlot == null || hoveredSlot.hasItem()) {
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

    /** Title + "hold to edit" hint, same two-line shape as {@link #renderButtonTooltip}. */
    private void renderPulseBoxTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (pulseLengthPopupOpen || !isOverPulseBox(mouseX, mouseY)) {
            return;
        }
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("dynamickeycards.card_reader.pulse_length"));
        tooltip.add(Component.translatable("dynamickeycards.card_reader.pulse_length.hold_to_edit",
                        Component.keybind("key.use"))
                .withStyle(ChatFormatting.GRAY));
        graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    // ---- Pulse length adjustment overlay ----

    private void openPulseLengthPopup() {
        pulseLengthPopupOpen = true;
        pulseSoundCooldown = 0;

        int ticks = menu.getReader().getPulseLength();
        int row = 0;
        int value = ticks;
        if (ticks > 60 * 20) {
            row = 2;
            value = ticks / (60 * 20);
        } else if (ticks > 60) {
            row = 1;
            value = ticks / 20;
        }
        pulseHoverRow = row;
        pulseHoverValue = Mth.clamp(value, 0, PULSE_MAX_VALUE);

        pulseLabelWidth = 0;
        for (String key : PULSE_ROW_KEYS) {
            pulseLabelWidth = Math.max(pulseLabelWidth, font.width(pulseRowLabel(key)));
        }
        pulseMilestoneCount = PULSE_MAX_VALUE / PULSE_MILESTONE_INTERVAL + 1;
        pulseValueBarWidth = (PULSE_MAX_VALUE + 1) * PULSE_BAR_SCALE + 1 + pulseMilestoneCount * PULSE_MILESTONE_SIZE;
        pulseRowsHeight = PULSE_ROW_KEYS.length * PULSE_ROW_HEIGHT;

        pulseBoxW = pulseLabelWidth + 14 + pulseValueBarWidth + 10;
        // 17px above rowsY (title) + 16px below (hint) - same proportions as the reference
        // layout, instead of the title crowding the box's own top edge
        pulseBoxH = 17 + pulseRowsHeight + 16;
        pulseBoxX = (this.width - pulseBoxW) / 2;
        pulseBoxY = (this.height - pulseBoxH) / 2;
        pulsePopupX = pulseBoxX + 6;

        pulseRowsY = pulseBoxY + 17;
        pulseHintY = pulseRowsY + pulseRowsHeight + 6;

        warpCursorToPulseValue(pulseHoverRow, pulseHoverValue);
    }

    private Component pulseRowLabel(String key) {
        return Component.translatable("dynamickeycards.card_reader.pulse_length." + key);
    }

    private static String formatPulseValue(int row, int value) {
        return switch (row) {
            case 0 -> value + "t";
            case 1 -> "0:" + (value < 10 ? "0" : "") + value;
            default -> value + ":00";
        };
    }

    /** X coordinate (absolute screen space) of a given column along the currently open bar. */
    private double pulseCoordX(int column) {
        int milestonesPassed = (Math.max(1, column) - 1) / PULSE_MILESTONE_INTERVAL;
        double xOut = milestonesPassed * PULSE_MILESTONE_SIZE + column * PULSE_BAR_SCALE + 1.5;
        if (column % PULSE_MILESTONE_INTERVAL == 0) {
            xOut += PULSE_MILESTONE_SIZE / 2.0;
        }
        if (column > 0) {
            xOut += PULSE_MILESTONE_SIZE;
        }
        return pulsePopupX + pulseLabelWidth + 14 + 4 + xOut;
    }

    private double pulseCoordY(int row) {
        return pulseRowsY + (row + 0.5) * PULSE_ROW_HEIGHT - 0.5;
    }

    private void warpCursorToPulseValue(int row, int value) {
        double x = pulseCoordX(value);
        double y = pulseCoordY(row);
        Window window = minecraft.getWindow();
        double guiScale = window.getGuiScale();
        GLFW.glfwSetCursorPos(window.getWindow(), x * guiScale, y * guiScale);
    }

    /** Finds the row closest to mouseY, then the column (in that row) closest to mouseX. */
    private void updatePulseHoverFromMouse(double mouseX, double mouseY) {
        boolean milestonesOnly = hasShiftDown();

        int row = 0;
        double bestDiff = Double.MAX_VALUE;
        for (; row < PULSE_ROW_KEYS.length; row++) {
            double diff = Math.abs(pulseCoordY(row) - mouseY);
            if (bestDiff < diff) {
                break;
            }
            bestDiff = diff;
        }
        row = Mth.clamp(row - 1, 0, PULSE_ROW_KEYS.length - 1);

        int column = 0;
        bestDiff = Double.MAX_VALUE;
        for (; column <= PULSE_MAX_VALUE; column++) {
            int probe = milestonesOnly ? column * PULSE_MILESTONE_INTERVAL : column;
            if (probe > PULSE_MAX_VALUE) {
                break;
            }
            double diff = Math.abs(pulseCoordX(probe) - mouseX);
            if (bestDiff < diff) {
                break;
            }
            bestDiff = diff;
        }
        column -= 1;
        int value = milestonesOnly ? column * PULSE_MILESTONE_INTERVAL : column;
        value = Mth.clamp(value, 0, PULSE_MAX_VALUE);
        if (row != pulseHoverRow || value != pulseHoverValue) {
            pulseHoverRow = row;
            pulseHoverValue = value;
            playPulseScrollSound();
        }
    }

    private boolean isOverPulseBox(double mouseX, double mouseY) {
        int x = leftPos + PULSE_BOX_X;
        int y = topPos + BUTTON_Y;
        return mouseX >= x && mouseX < x + PULSE_BOX_W && mouseY >= y && mouseY < y + PULSE_BOX_H;
    }

    /** No-op (silent) unless Create happens to be installed - see {@link #SCROLL_SOUND_ID}. */
    private void playPulseScrollSound() {
        if (pulseSoundCooldown > 0) {
            return;
        }
        BuiltInRegistries.SOUND_EVENT.getOptional(SCROLL_SOUND_ID).ifPresent(sound -> {
            float pitch = Mth.lerp(pulseHoverValue / (float) PULSE_MAX_VALUE, 1.15f, 1.5f);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.25f));
            pulseSoundCooldown = 1;
        });
    }

    private void confirmAndClosePulseLengthPopup() {
        int multiplier = PULSE_ROW_MULTIPLIER[pulseHoverRow];
        int ticks = Math.max(1, pulseHoverValue) * multiplier;
        ticks = Mth.clamp(ticks, 1, 72000);
        sendButtonClick(CardReaderConfigMenu.PULSE_LENGTH_ID_BASE + ticks);
        pulseLengthPopupOpen = false;
    }

    /**
     * Drawn on a raised Z plane so it sits above the player-inventory item icons underneath -
     * those render at their own elevated Z (see {@link #renderDeviceIcon}), and a flat overlay
     * left at Z 0 would otherwise get drawn over by them.
     */
    private void renderPulseLengthPopup(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        renderPulseLengthPopupContent(graphics);
        graphics.pose().popPose();
    }

    private void renderPulseLengthPopupContent(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0x90000000);
        blitStretched(graphics, pulseBoxX, pulseBoxY, pulseBoxW, pulseBoxH, TEX_OUTER_BG);

        Component title = Component.translatable("dynamickeycards.card_reader.pulse_length");
        graphics.drawCenteredString(font, title, pulseBoxX + pulseBoxW / 2, pulseRowsY - 14, 0xFFFFFF);

        int barFrameX = pulsePopupX + pulseLabelWidth + 14;
        renderFrame(graphics, barFrameX, pulseRowsY - 3, pulseValueBarWidth + 8, pulseRowsHeight + 5);
        blitStretched(graphics, barFrameX + 3, pulseRowsY, pulseValueBarWidth + 2, pulseRowsHeight - 1, TEX_BAR_BG);

        for (int row = 0; row < PULSE_ROW_KEYS.length; row++) {
            int rowY = pulseRowsY + row * PULSE_ROW_HEIGHT;
            boolean selected = row == pulseHoverRow;

            blitCropped(graphics, pulsePopupX - 4, rowY, pulseLabelWidth + 8, 11,
                    selected ? TEX_LABEL_BG_SELECTED : TEX_LABEL_BG);

            Tex barTex = selected ? TEX_BAR_SELECTED : TEX_BAR;
            int valueBarX = pulsePopupX + pulseLabelWidth + 14 + 4;
            for (int w = 0; w < pulseValueBarWidth; w += barTex.w() - 1) {
                int segW = Math.min(barTex.w() - 1, pulseValueBarWidth - w);
                blitCropped(graphics, valueBarX + w, rowY + 1, segW, 8, barTex);
            }

            graphics.drawString(font, pulseRowLabel(PULSE_ROW_KEYS[row]),
                    selected ? pulsePopupX + 3 : pulsePopupX, rowY + 1,
                    selected ? 0xF0F0F4 : 0x7A7A80, false);

            Tex milestoneTex = selected ? TEX_MILESTONE_SELECTED : TEX_MILESTONE;
            int milestoneX = valueBarX;
            for (int m = 0; m < pulseMilestoneCount; m++) {
                blitNative(graphics, milestoneX, rowY + 1, milestoneTex);
                milestoneX += PULSE_MILESTONE_SIZE + PULSE_MILESTONE_INTERVAL * PULSE_BAR_SCALE;
            }
        }

        renderFrame(graphics, pulsePopupX - 7, pulseRowsY - 3, pulseLabelWidth + 14, pulseRowsHeight + 5);

        String cursorText = formatPulseValue(pulseHoverRow, pulseHoverValue);
        int cursorWidth = (font.width(cursorText) / 2) * 2 + 3;
        int cursorX = (int) pulseCoordX(pulseHoverValue) - cursorWidth / 2;
        int cursorY = (int) pulseCoordY(pulseHoverRow) - 7;
        blitNative(graphics, cursorX - 3, cursorY, TEX_CURSOR_LEFT);
        blitCropped(graphics, cursorX, cursorY, cursorWidth, 14, TEX_CURSOR);
        blitNative(graphics, cursorX + cursorWidth, cursorY, TEX_CURSOR_RIGHT);
        graphics.drawString(font, cursorText, cursorX + 2, cursorY + 3, 0xF0F0F4, false);

        Component hint = Component.translatable("dynamickeycards.card_reader.pulse_length.hint",
                Component.keybind("key.use"));
        graphics.drawCenteredString(font, hint, pulseBoxX + pulseBoxW / 2, pulseHintY, 0xFFFFFF);
    }

    /** Four fixed corners + stretched/cropped edges - a standard 9-slice border. */
    private void renderFrame(GuiGraphics graphics, int x, int y, int w, int h) {
        blitNative(graphics, x, y, TEX_FRAME_TL);
        blitNative(graphics, x + w - 4, y, TEX_FRAME_TR);
        blitNative(graphics, x, y + h - 4, TEX_FRAME_BL);
        blitNative(graphics, x + w - 4, y + h - 4, TEX_FRAME_BR);
        if (h > 8) {
            blitStretched(graphics, x, y + 4, 3, h - 8, TEX_FRAME_LEFT);
            blitStretched(graphics, x + w - 3, y + 4, 3, h - 8, TEX_FRAME_RIGHT);
        }
        if (w > 8) {
            blitCropped(graphics, x + 4, y, w - 8, 3, TEX_FRAME_TOP);
            blitCropped(graphics, x + 4, y + h - 3, w - 8, 3, TEX_FRAME_BOTTOM);
        }
    }

    /** Draws {@code tex} at its native size (1:1, no scaling). */
    private void blitNative(GuiGraphics graphics, int x, int y, Tex tex) {
        blitCropped(graphics, x, y, tex.w(), tex.h(), tex);
    }

    /** Crops a {@code w}x{@code h} chunk starting at the region's (u, v), drawn at 1:1 scale. */
    private void blitCropped(GuiGraphics graphics, int x, int y, int w, int h, Tex tex) {
        graphics.blit(PULSE_TEX, x, y, tex.u(), tex.v(), w, h, 256, 256);
    }

    /** Stretches the region's full (w, h) texture area to fill an arbitrary destination size. */
    private void blitStretched(GuiGraphics graphics, int x, int y, int w, int h, Tex tex) {
        graphics.blit(PULSE_TEX, x, y, w, h, tex.u(), tex.v(), tex.w(), tex.h(), 256, 256);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pulseLengthPopupOpen) {
            return true;
        }
        if (button == 1 && isOverPulseBox(mouseX, mouseY)) {
            pulseBoxHeldTicks = 0;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (pulseLengthPopupOpen) {
            if (button == 1) {
                confirmAndClosePulseLengthPopup();
            }
            return true;
        }
        if (button == 1) {
            pulseBoxHeldTicks = -1;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (pulseLengthPopupOpen) {
            updatePulseHoverFromMouse(mouseX, mouseY);
            return;
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pulseLengthPopupOpen) {
            int step = hasShiftDown() ? PULSE_MILESTONE_INTERVAL : 1;
            int delta = (int) Math.signum(scrollY) * step;
            int newValue = Mth.clamp(pulseHoverValue + delta, 0, PULSE_MAX_VALUE);
            if (newValue != pulseHoverValue) {
                pulseHoverValue = newValue;
                warpCursorToPulseValue(pulseHoverRow, pulseHoverValue);
                playPulseScrollSound();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
