package com.mbx.dynamickeycards.menu;

import com.mbx.dynamickeycards.DynamicKeycards;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The 18x18 icon button shared by every device config screen in this mod: a square that draws
 * one of {@link #WIDGETS}' four background states under one of {@link #ICONS}' 16x16 sprites.
 *
 * <p>The four background states are picked in {@link #renderWidget}: pressed-while-hovered,
 * hovered, {@link #green} (this button's mode is the active one), and plain. An inactive button
 * ({@code active == false}) draws the plain state plus a dimming overlay instead of any
 * hover/press feedback - only {@link LinkDeviceScreen} ever deactivates a button (a reader-bound
 * sensor's locked mode buttons, and the Create-only modes when Create isn't installed), so for
 * the other two screens that path is simply never taken.
 */
class ModeButton extends AbstractButton {

    /** Physical-redstone-only: the reader/sensor's "normal" mode and the transmitter's "redstone only" mode both use it. */
    static final int ICON_REDSTONE = 0;
    /** Create Redstone Link (antenna sprite) - {@link LinkDeviceScreen}'s link-only mode. */
    static final int ICON_LINK = 16;
    /** Both outputs at once - {@link LinkDeviceScreen}'s simultaneous mode. */
    static final int ICON_SIMULTANEOUS = 32;
    static final int ICON_RESET = 48;
    static final int ICON_CONFIRM = 64;
    /** The transmitter's mixed mode. */
    static final int ICON_MANUAL = 80;
    /** The receiver's pulse mode. */
    static final int ICON_BUTTON = 96;
    /** The receiver's toggle mode. */
    static final int ICON_LEVER = 112;

    private static final ResourceLocation WIDGETS =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/broadcast_widgets.png");
    private static final ResourceLocation ICONS =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/broadcast_icons.png");

    private static final int SIZE = 18;
    /** Widths of the two sheets, needed by {@code blit}'s explicit-size form. */
    private static final int WIDGETS_SHEET_WIDTH = 72;
    private static final int ICONS_SHEET_WIDTH = 128;
    /** u offsets of {@link #WIDGETS}' four states. */
    private static final int STATE_PLAIN = 0;
    private static final int STATE_HOVER = 18;
    private static final int STATE_GREEN = 36;
    private static final int STATE_PRESSED = 54;
    /** Dimming overlay drawn over an inactive button - see the class doc. */
    private static final int INACTIVE_OVERLAY = 0x90000000;

    private final int iconU;
    private final Runnable onPress;

    /**
     * Translation-key suffix for this button's tooltip, resolved by whichever screen owns it
     * (each prefixes its own device's namespace). Not final: {@link LinkDeviceScreen} repoints it
     * every tick depending on the device/binding state, see its own doc. {@code null} on buttons
     * that show no tooltip at all.
     */
    String titleKey;
    /** Whether this button's mode is the currently-selected one - drives the {@link #STATE_GREEN} background. */
    boolean green;
    /** Nudges the icon horizontally, for a sprite that isn't centered in its own 16x16 cell. */
    int iconOffsetX;

    private boolean pressed;

    ModeButton(int x, int y, int iconU, String titleKey, Runnable onPress) {
        super(x, y, SIZE, SIZE, Component.empty());
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
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(WIDGETS, getX(), getY(), backgroundStateU(), 0, SIZE, SIZE, WIDGETS_SHEET_WIDTH, SIZE);
        graphics.blit(ICONS, getX() + 1 + iconOffsetX, getY() + 1, iconU, 0, 16, 16, ICONS_SHEET_WIDTH, 16);
        if (!active) {
            // dim on top rather than skip the icon entirely, so it stays readable
            graphics.fill(getX(), getY(), getX() + SIZE, getY() + SIZE, INACTIVE_OVERLAY);
        }
    }

    /** Which of {@link #WIDGETS}' four states this button draws right now - see the class doc. */
    private int backgroundStateU() {
        if (!active) {
            // locked out - flat, with no hover/press feedback
            return STATE_PLAIN;
        }
        if (isHovered && pressed) {
            return STATE_PRESSED;
        }
        if (isHovered) {
            return STATE_HOVER;
        }
        return green ? STATE_GREEN : STATE_PLAIN;
    }
}
