package com.mbx.dynamickeycards.menu;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The full-screen duration picker shared by every device config screen that has a tick-valued
 * setting: the reader/sensor's signal length ({@link LinkDeviceScreen}), the receiver's release
 * delay ({@link ReceiverScreen}), and the transmitter's manual-trigger duration
 * ({@link TransmitterScreen}).
 *
 * <p>Three rows - ticks, seconds, minutes - each a bar of {@link #MAX_VALUE}+1 columns with a
 * milestone tick every {@link #MILESTONE_INTERVAL}. The mouse picks a row and column by proximity
 * ({@link #updateHoverFromMouse}); holding shift snaps to milestones only. Releasing right-click
 * commits {@code column * } the row's multiplier as the new tick value.
 *
 * <p>Opening it warps the cursor onto the column matching the current value, so the picker starts
 * under the pointer rather than making the player hunt for where they already are.
 *
 * <p>The owning screen keeps the trigger box (the small number readout that is held to open this)
 * and routes its mouse/tick callbacks here; everything from {@link #open()} onward lives in this
 * class. Only three things vary per screen, all constructor arguments: where the current value is
 * read from, where a committed value is sent, and the heading to draw.
 */
class DurationPopup {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/pulse_length.png");
    /**
     * The per-column scrub tick. Deliberately plain {@code minecraft:block.note_block.hat}: short
     * and untuned, so dragging across all 61 columns reads as a scrub rather than a melody, and it
     * keeps this mod's "vanilla sounds only, no custom assets" rule (see {@code DKSounds}) intact.
     *
     * <p>Don't route this through Create's {@code create:scroll_value} - that is only an alias of
     * this same vanilla event, so it can produce nothing different while leaving Create-less
     * installs silent.
     */
    private static final Holder.Reference<SoundEvent> SCROLL_SOUND = SoundEvents.NOTE_BLOCK_HAT;

    private static final int MAX_VALUE = 60;
    private static final int MILESTONE_INTERVAL = 10;
    private static final int BAR_SCALE = 2;
    private static final int MILESTONE_SIZE = 4;
    private static final int ROW_HEIGHT = 11;
    /** Ticks per unit on each row, parallel to {@link #ROW_KEYS}. */
    private static final int[] ROW_MULTIPLIER = {1, 20, 1200};
    private static final String[] ROW_KEYS = {"ticks", "seconds", "minutes"};
    /** Hard ceiling on a committed value - 72000 ticks = one hour. */
    private static final int MAX_TICKS = 72000;
    /** Z plane for the overlay, so it covers the item icons the screens draw at their own raised Z. */
    private static final int POPUP_Z = 400;
    private static final int SCRIM_COLOR = 0x90000000;

    /** A (u, v, width, height) region of {@link #TEXTURE} (256x256). */
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

    private final Screen screen;
    private final IntSupplier currentTicks;
    private final IntConsumer commitTicks;
    private final Supplier<Component> title;

    private boolean open;
    private int labelWidth;
    private int valueBarWidth;
    private int popupX;
    private int boxX;
    private int boxY;
    private int boxW;
    private int boxH;
    private int rowsY;
    private int rowsHeight;
    private int milestoneCount;
    private int hintY;
    private int hoverRow;
    private int hoverValue;
    /** At-most-one-sound-per-tick guard, so a fast drag across columns doesn't machine-gun the sound. */
    private int soundCooldown;

    DurationPopup(Screen screen, IntSupplier currentTicks, IntConsumer commitTicks, Supplier<Component> title) {
        this.screen = screen;
        this.currentTicks = currentTicks;
        this.commitTicks = commitTicks;
        this.title = title;
    }

    boolean isOpen() {
        return open;
    }

    private Font font() {
        return Minecraft.getInstance().font;
    }

    /** Counts the sound guard down - call once per screen tick. */
    void tick() {
        if (soundCooldown > 0) {
            soundCooldown--;
        }
    }

    /**
     * Lays the picker out around the current value and warps the cursor onto it. The layout is
     * measured rather than hardcoded, since the row labels are translated and their widest one
     * decides how far right the bars start.
     */
    void open() {
        open = true;
        soundCooldown = 0;

        seedHoverFromTicks(currentTicks.getAsInt());

        labelWidth = 0;
        for (String key : ROW_KEYS) {
            labelWidth = Math.max(labelWidth, font().width(rowLabel(key)));
        }
        milestoneCount = MAX_VALUE / MILESTONE_INTERVAL + 1;
        valueBarWidth = (MAX_VALUE + 1) * BAR_SCALE + 1 + milestoneCount * MILESTONE_SIZE;
        rowsHeight = ROW_KEYS.length * ROW_HEIGHT;

        boxW = labelWidth + 14 + valueBarWidth + 10;
        // 17px above the rows (title) + 16px below (hint) - same proportions as the reference
        // layout, instead of the title crowding the box's own top edge
        boxH = 17 + rowsHeight + 16;
        boxX = (screen.width - boxW) / 2;
        boxY = (screen.height - boxH) / 2;
        popupX = boxX + 6;

        rowsY = boxY + 17;
        hintY = rowsY + rowsHeight + 6;

        warpCursorToValue(hoverRow, hoverValue);
    }

    /** Picks the coarsest row that can express {@code ticks} without going off the end of the bar. */
    private void seedHoverFromTicks(int ticks) {
        int row = 0;
        int value = ticks;
        if (ticks > 60 * 20) {
            row = 2;
            value = ticks / (60 * 20);
        } else if (ticks > 60) {
            row = 1;
            value = ticks / 20;
        }
        hoverRow = row;
        hoverValue = Mth.clamp(value, 0, MAX_VALUE);
    }

    private Component rowLabel(String key) {
        return Component.translatable("dynamickeycards.link_device.signal_length." + key);
    }

    private static String formatValue(int row, int value) {
        return switch (row) {
            case 0 -> value + "t";
            case 1 -> "0:" + (value < 10 ? "0" : "") + value;
            default -> value + ":00";
        };
    }

    /** X coordinate (absolute screen space) of a given column along the currently open bar. */
    private double coordX(int column) {
        int milestonesPassed = (Math.max(1, column) - 1) / MILESTONE_INTERVAL;
        double xOut = milestonesPassed * MILESTONE_SIZE + column * BAR_SCALE + 1.5;
        if (column % MILESTONE_INTERVAL == 0) {
            xOut += MILESTONE_SIZE / 2.0;
        }
        if (column > 0) {
            xOut += MILESTONE_SIZE;
        }
        return popupX + labelWidth + 14 + 4 + xOut;
    }

    private double coordY(int row) {
        return rowsY + (row + 0.5) * ROW_HEIGHT - 0.5;
    }

    private void warpCursorToValue(int row, int value) {
        double x = coordX(value);
        double y = coordY(row);
        Window window = Minecraft.getInstance().getWindow();
        double guiScale = window.getGuiScale();
        GLFW.glfwSetCursorPos(window.getWindow(), x * guiScale, y * guiScale);
    }

    /** Finds the row closest to mouseY, then the column (in that row) closest to mouseX. */
    private void updateHoverFromMouse(double mouseX, double mouseY) {
        boolean milestonesOnly = Screen.hasShiftDown();

        int row = 0;
        double bestDiff = Double.MAX_VALUE;
        for (; row < ROW_KEYS.length; row++) {
            double diff = Math.abs(coordY(row) - mouseY);
            if (bestDiff < diff) {
                break;
            }
            bestDiff = diff;
        }
        row = Mth.clamp(row - 1, 0, ROW_KEYS.length - 1);

        int column = 0;
        bestDiff = Double.MAX_VALUE;
        for (; column <= MAX_VALUE; column++) {
            int probe = milestonesOnly ? column * MILESTONE_INTERVAL : column;
            if (probe > MAX_VALUE) {
                break;
            }
            double diff = Math.abs(coordX(probe) - mouseX);
            if (bestDiff < diff) {
                break;
            }
            bestDiff = diff;
        }
        column -= 1;
        int value = milestonesOnly ? column * MILESTONE_INTERVAL : column;
        value = Mth.clamp(value, 0, MAX_VALUE);
        if (row != hoverRow || value != hoverValue) {
            hoverRow = row;
            hoverValue = value;
            playScrollSound();
        }
    }

    /** A short vanilla tick per column change, rising in pitch with the value - see {@link #SCROLL_SOUND}. */
    private void playScrollSound() {
        if (soundCooldown > 0) {
            return;
        }
        float pitch = Mth.lerp(hoverValue / (float) MAX_VALUE, 1.15f, 1.5f);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SCROLL_SOUND.value(), pitch, 0.25f));
        soundCooldown = 1;
    }

    private void confirmAndClose() {
        // 0 is a legitimate choice (shown as "0t") - not floored up to 1
        int ticks = Mth.clamp(hoverValue * ROW_MULTIPLIER[hoverRow], 0, MAX_TICKS);
        commitTicks.accept(ticks);
        open = false;
    }

    // ---- Input routing: each returns true when the popup consumed the event ----

    boolean mouseClicked() {
        return open;
    }

    boolean mouseReleased(int button) {
        if (!open) {
            return false;
        }
        if (button == 1) {
            confirmAndClose();
        }
        return true;
    }

    boolean mouseMoved(double mouseX, double mouseY) {
        if (!open) {
            return false;
        }
        updateHoverFromMouse(mouseX, mouseY);
        return true;
    }

    boolean mouseScrolled(double scrollY) {
        if (!open) {
            return false;
        }
        int step = Screen.hasShiftDown() ? MILESTONE_INTERVAL : 1;
        int delta = (int) Math.signum(scrollY) * step;
        int newValue = Mth.clamp(hoverValue + delta, 0, MAX_VALUE);
        if (newValue != hoverValue) {
            hoverValue = newValue;
            warpCursorToValue(hoverRow, hoverValue);
            playScrollSound();
        }
        return true;
    }

    // ---- Rendering ----

    /**
     * Drawn on a raised Z plane so it sits above the player-inventory item icons underneath -
     * those render at their own elevated Z, and a flat overlay left at Z 0 would otherwise get
     * drawn over by them.
     */
    void render(GuiGraphics graphics) {
        if (!open) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, POPUP_Z);
        renderContent(graphics);
        graphics.pose().popPose();
    }

    private void renderContent(GuiGraphics graphics) {
        Font font = font();
        graphics.fill(0, 0, screen.width, screen.height, SCRIM_COLOR);
        blitStretched(graphics, boxX, boxY, boxW, boxH, TEX_OUTER_BG);

        graphics.drawCenteredString(font, title.get(), boxX + boxW / 2, rowsY - 14, 0xFFFFFF);

        int barFrameX = popupX + labelWidth + 14;
        renderFrame(graphics, barFrameX, rowsY - 3, valueBarWidth + 8, rowsHeight + 5);
        blitStretched(graphics, barFrameX + 3, rowsY, valueBarWidth + 2, rowsHeight - 1, TEX_BAR_BG);

        for (int row = 0; row < ROW_KEYS.length; row++) {
            int rowY = rowsY + row * ROW_HEIGHT;
            boolean selected = row == hoverRow;

            blitCropped(graphics, popupX - 4, rowY, labelWidth + 8, 11,
                    selected ? TEX_LABEL_BG_SELECTED : TEX_LABEL_BG);

            Tex barTex = selected ? TEX_BAR_SELECTED : TEX_BAR;
            int valueBarX = popupX + labelWidth + 14 + 4;
            for (int w = 0; w < valueBarWidth; w += barTex.w() - 1) {
                int segW = Math.min(barTex.w() - 1, valueBarWidth - w);
                blitCropped(graphics, valueBarX + w, rowY + 1, segW, 8, barTex);
            }

            graphics.drawString(font, rowLabel(ROW_KEYS[row]),
                    selected ? popupX + 3 : popupX, rowY + 1,
                    selected ? 0xF0F0F4 : 0x7A7A80, false);

            Tex milestoneTex = selected ? TEX_MILESTONE_SELECTED : TEX_MILESTONE;
            int milestoneX = valueBarX;
            for (int m = 0; m < milestoneCount; m++) {
                blitNative(graphics, milestoneX, rowY + 1, milestoneTex);
                milestoneX += MILESTONE_SIZE + MILESTONE_INTERVAL * BAR_SCALE;
            }
        }

        renderFrame(graphics, popupX - 7, rowsY - 3, labelWidth + 14, rowsHeight + 5);

        String cursorText = formatValue(hoverRow, hoverValue);
        int cursorWidth = (font.width(cursorText) / 2) * 2 + 3;
        int cursorX = (int) coordX(hoverValue) - cursorWidth / 2;
        int cursorY = (int) coordY(hoverRow) - 7;
        blitNative(graphics, cursorX - 3, cursorY, TEX_CURSOR_LEFT);
        blitCropped(graphics, cursorX, cursorY, cursorWidth, 14, TEX_CURSOR);
        blitNative(graphics, cursorX + cursorWidth, cursorY, TEX_CURSOR_RIGHT);
        graphics.drawString(font, cursorText, cursorX + 2, cursorY + 3, 0xF0F0F4, false);

        Component hint = Component.translatable("dynamickeycards.link_device.signal_length.hint",
                Component.keybind("key.use"));
        graphics.drawCenteredString(font, hint, boxX + boxW / 2, hintY, 0xFFFFFF);
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
        graphics.blit(TEXTURE, x, y, tex.u(), tex.v(), w, h, 256, 256);
    }

    /** Stretches the region's full (w, h) texture area to fill an arbitrary destination size. */
    private void blitStretched(GuiGraphics graphics, int x, int y, int w, int h, Tex tex) {
        graphics.blit(TEXTURE, x, y, w, h, tex.u(), tex.v(), tex.w(), tex.h(), 256, 256);
    }
}
