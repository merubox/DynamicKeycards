package com.mbx.dynamickeycards.menu;

import com.mbx.dynamickeycards.DKTooltips;
import com.mbx.dynamickeycards.DynamicKeycards;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * The shared config screen for a device whose whole UI is "pick one of N modes, plus some
 * tick-valued durations" - the receiver ({@link ReceiverScreen}), the transmitter
 * ({@link TransmitterScreen}), and the siren ({@link SirenScreen}). The first two were the same
 * 570-line screen with two names; everything that actually differs between them is now the small
 * subclass contract below.
 *
 * <p>Layout is {@code LinkDeviceScreen}'s, reused wholesale: the same background panel and
 * player-inventory panel textures, the same button row at y={@value #BUTTON_Y} with mode buttons
 * from x={@value #FIRST_MODE_BUTTON_X} spaced {@value #MODE_BUTTON_SPACING} apart, reset at
 * x={@value #RESET_BUTTON_X} and done at x={@value #CONFIRM_BUTTON_X}. The confirm button does
 * nothing but close the screen - it exists because the shared background art already draws its
 * frame there, so leaving it unwired would look like dead, unclickable decoration.
 *
 * <p>Duration readouts sit wherever their {@link DurationSpec} puts them: one in the button row
 * for the receiver and transmitter ({@link DurationSpec#inButtonRow}), two stacked in the panel
 * for the siren - which is also why the background is a {@link #background()} the subclass can
 * repoint, since the boxes are drawn into the art rather than by this class.
 *
 * <p>A readout opens its own {@link DurationPopup} when right-click is held on it for
 * {@value #PULSE_HOLD_OPEN_TICKS} ticks; this class owns the hold counter and the hit test, and
 * routes the mouse callbacks into whichever popup is open.
 */
abstract class AbstractDeviceModeScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {

    /**
     * One mode button: which icon it draws, its tooltip's lang-key suffix, and whether it is the
     * device's current mode (checked every tick to drive the selected-state highlight). Its menu
     * button id is its index in {@link #modes()} - see {@link AbstractDeviceModeMenu}'s class doc
     * for that shared numbering.
     */
    protected record ModeSpec(int iconU, String titleKey, BooleanSupplier selected) {
    }

    /**
     * One duration readout: where its {@value #PULSE_BOX_W}x{@value #PULSE_BOX_H} box sits on the
     * panel, the lang-key suffix naming it (in both the tooltip and its picker's heading), how to
     * read its value, how that value reads inside the box, and the scale its picker offers. Its
     * menu button ids come from its index in {@link #durations()} - see
     * {@link AbstractDeviceModeMenu}'s class doc.
     */
    protected record DurationSpec(int x, int y, String key, IntSupplier ticks,
                                  IntFunction<String> readout, DurationPopup.Scale scale) {

        /** The common case: the one box in the button row, edited on the ticks/seconds/minutes picker. */
        protected static DurationSpec inButtonRow(String key, IntSupplier ticks) {
            return new DurationSpec(PULSE_BOX_X, BUTTON_Y, key, ticks,
                    AbstractDeviceModeScreen::formatTicksCompact, DurationPopup.TICKS_SECONDS_MINUTES);
        }
    }

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/broadcast_mode.png");
    private static final ResourceLocation PLAYER_INVENTORY =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/broadcast_player_inventory.png");

    private static final int BG_WIDTH = 184;
    private static final int BG_HEIGHT = 99;
    private static final int BUTTON_Y = 75;
    private static final int PLAYER_INV_WIDTH = 176;
    private static final int PLAYER_INV_HEIGHT = 108;
    private static final int TITLE_COLOR = 0x303030;

    private static final int FIRST_MODE_BUTTON_X = 7;
    private static final int MODE_BUTTON_SPACING = 18;
    private static final int RESET_BUTTON_X = 122;
    private static final int CONFIRM_BUTTON_X = 151;

    private static final int PULSE_BOX_X = 70;
    private static final int PULSE_BOX_W = 36;
    private static final int PULSE_BOX_H = 18;
    private static final int PULSE_HOLD_OPEN_TICKS = 5;

    /** Screen-space size of {@link #renderDeviceIcon} (16x16 art scaled 4x). */
    private static final int ICON_SIZE = 64;
    /**
     * Base Z for {@link #renderDeviceIcon}. {@code GuiGraphics#renderItem} adds 150 of its own Z,
     * and the uniform 4x scale multiplies that to 600 - so this offset is what lands the finished
     * icon at Z 200: above the background panel (Z 0), but below the Z 400 plane vanilla draws
     * tooltips on. The scale has to stay uniform (the block model is rotated in its GUI transform,
     * so scaling Z differently from X/Y would shear it), which is why this is corrected on the
     * base offset instead.
     */
    private static final int ICON_Z = -400;

    private final List<ModeButton> modeButtons = new ArrayList<>();
    private final List<DurationPopup> durationPopups = new ArrayList<>();
    private List<ModeSpec> modeSpecs = List.of();
    private List<DurationSpec> durationSpecs = List.of();
    /** Which readout right-click is being held on, or -1 for none. */
    private int heldBox = -1;
    private int heldTicks;

    protected AbstractDeviceModeScreen(M menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = BG_WIDTH;
        this.imageHeight = BG_HEIGHT + 4 + PLAYER_INV_HEIGHT;
    }

    // ---- Subclass contract ----

    /** This device's block entity - used for its block's icon; mode/duration come through the methods below. */
    protected abstract BlockEntity device();

    /** Lang namespace for this device's own tooltip keys, e.g. {@code "receiver"}. */
    protected abstract String langNamespace();

    /** The mode buttons, left to right. Read once in {@link #init()}. */
    protected abstract List<ModeSpec> modes();

    /**
     * The duration readouts. Read once in {@link #init()}, and must stay in the same order as
     * {@link AbstractDeviceModeMenu#durations()} - the index is the button id's range.
     */
    protected abstract List<DurationSpec> durations();

    /** The background panel art. Overridden by a layout whose boxes aren't where the default art draws them. */
    protected ResourceLocation background() {
        return BACKGROUND;
    }

    /** Full lang key for one of this device's own suffixes. */
    private String langKey(String suffix) {
        return "dynamickeycards." + langNamespace() + "." + suffix;
    }

    // ---- Setup ----

    @Override
    protected void init() {
        super.init();
        // nudges the window after centering to land at the intended position
        this.leftPos += 4;
        this.topPos += 5;

        modeButtons.clear();
        modeSpecs = List.copyOf(modes());
        for (int i = 0; i < modeSpecs.size(); i++) {
            ModeSpec spec = modeSpecs.get(i);
            // the button id is the index - see AbstractDeviceModeMenu's class doc
            int buttonId = i;
            modeButtons.add(addRenderableWidget(new ModeButton(
                    leftPos + FIRST_MODE_BUTTON_X + i * MODE_BUTTON_SPACING, topPos + BUTTON_Y,
                    spec.iconU(), spec.titleKey(), () -> sendButtonClick(buttonId))));
        }

        // a resize re-runs init(), so the popups are rebuilt and any hold in progress is stale
        durationPopups.clear();
        heldBox = -1;
        durationSpecs = List.copyOf(durations());
        for (int i = 0; i < durationSpecs.size(); i++) {
            DurationSpec spec = durationSpecs.get(i);
            int idBase = AbstractDeviceModeMenu.DURATION_ID_BASE + i * AbstractDeviceModeMenu.DURATION_ID_STRIDE;
            durationPopups.add(new DurationPopup(this, spec.ticks(),
                    ticks -> sendButtonClick(idBase + ticks),
                    () -> Component.translatable(langKey(spec.key())), spec.scale()));
        }

        addRenderableWidget(new ModeButton(leftPos + RESET_BUTTON_X, topPos + BUTTON_Y, ModeButton.ICON_RESET,
                null, () -> sendButtonClick(AbstractDeviceModeMenu.BUTTON_RESET)));
        // closes the screen only - see the class doc for why it's wired at all
        addRenderableWidget(new ModeButton(leftPos + CONFIRM_BUTTON_X, topPos + BUTTON_Y, ModeButton.ICON_CONFIRM,
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
        durationPopups.forEach(DurationPopup::tick);
    }

    /** The popup currently on screen, if any - at most one is ever open. */
    private DurationPopup openPopup() {
        for (DurationPopup popup : durationPopups) {
            if (popup.isOpen()) {
                return popup;
            }
        }
        return null;
    }

    /** Repoints the selected-state highlight at whichever mode the device currently reports. */
    private void updateModeIndicators() {
        for (int i = 0; i < modeButtons.size(); i++) {
            modeButtons.get(i).green = modeSpecs.get(i).selected().getAsBoolean();
        }
    }

    private void tickPulseBoxHold() {
        if (heldBox < 0 || openPopup() != null) {
            return;
        }
        if (heldTicks++ >= PULSE_HOLD_OPEN_TICKS) {
            durationPopups.get(heldBox).open();
            heldBox = -1;
        }
    }

    // ---- Rendering ----

    /** Suppresses vanilla's default title/inventory labels - both are drawn manually in {@link #renderBg}. */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(background(), leftPos, topPos, 0, 0, BG_WIDTH, BG_HEIGHT, BG_WIDTH, BG_HEIGHT);

        int titleX = (BG_WIDTH - 8) / 2 - font.width(title) / 2;
        graphics.drawString(font, title, leftPos + titleX, topPos + 4, TITLE_COLOR, false);

        int invX = leftPos;
        int invY = topPos + BG_HEIGHT + 4;
        graphics.blit(PLAYER_INVENTORY, invX, invY, 0, 0, PLAYER_INV_WIDTH, PLAYER_INV_HEIGHT,
                PLAYER_INV_WIDTH, PLAYER_INV_HEIGHT);
        graphics.drawString(font, playerInventoryTitle, invX + 8, invY + 6, 0x404040, false);

        renderDeviceIcon(graphics);
        for (DurationSpec spec : durationSpecs) {
            renderDurationBox(graphics, spec);
        }
    }

    /** A readout's current value - the box it sits in is part of {@link #background()}. */
    private void renderDurationBox(GuiGraphics graphics, DurationSpec spec) {
        String text = spec.readout().apply(spec.ticks().getAsInt());
        graphics.drawCenteredString(font, text, leftPos + spec.x() + PULSE_BOX_W / 2,
                topPos + spec.y() + (PULSE_BOX_H - 8) / 2, 0xFFFFFF);
    }

    /** Coarsest unit that divides the value exactly - {@code 100} reads as {@code 5s}, {@code 7} as {@code 7t}. */
    static String formatTicksCompact(int ticks) {
        if (ticks % 1200 == 0) {
            return (ticks / 1200) + "m";
        }
        if (ticks % 20 == 0) {
            return (ticks / 20) + "s";
        }
        return ticks + "t";
    }

    /** A scaled-up icon of this device's block, rendered next to the arrow. */
    private void renderDeviceIcon(GuiGraphics graphics) {
        ItemStack stack = new ItemStack(device().getBlockState().getBlock().asItem());
        graphics.pose().pushPose();
        graphics.pose().translate(getIconAreaX(), getIconAreaY(), ICON_Z);
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

    /** Screen-space bounds of the device icon, so an EMI plugin can register it as an exclusion area. */
    public int[] getDeviceIconScreenBounds() {
        return new int[] {getIconAreaX(), getIconAreaY(), ICON_SIZE, ICON_SIZE};
    }

    /** Whole-screen bounds while the duration popup is open, {@code null} otherwise - same EMI exclusion reasoning. */
    public int[] getPulseLengthPopupScreenBounds() {
        return openPopup() != null ? new int[] {0, 0, this.width, this.height} : null;
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (openPopup() != null) {
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        for (ModeButton button : modeButtons) {
            renderButtonTooltip(graphics, button, mouseX, mouseY);
        }
        for (DurationSpec spec : durationSpecs) {
            renderDurationBoxTooltip(graphics, spec, mouseX, mouseY);
        }
        durationPopups.forEach(popup -> popup.render(graphics));
    }

    /**
     * Mode name plus a "Hold [Shift] for Summary" expansion explaining what the mode does - the
     * same two-part shape {@code LinkDeviceScreen}'s own mode buttons use, so every device's
     * buttons read alike. The summary line's key is the mode key with {@code 1} appended, matching
     * {@link DKTooltips#summary}'s convention elsewhere in the mod.
     */
    private void renderButtonTooltip(GuiGraphics graphics, ModeButton button, int mouseX, int mouseY) {
        if (openPopup() != null || !button.isMouseOver(mouseX, mouseY)) {
            return;
        }
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(langKey(button.titleKey)));
        DKTooltips.summary(tooltip, button.titleKey + "1");
        graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    private void renderDurationBoxTooltip(GuiGraphics graphics, DurationSpec spec, int mouseX, int mouseY) {
        if (openPopup() != null || !isOverBox(spec, mouseX, mouseY)) {
            return;
        }
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(langKey(spec.key())));
        tooltip.add(Component.translatable("dynamickeycards.link_device.signal_length.hold_to_edit",
                        Component.keybind("key.use"))
                .withStyle(ChatFormatting.GRAY));
        graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    // ---- Input ----

    /** One readout's box - held with right-click to open its picker. */
    private boolean isOverBox(DurationSpec spec, double mouseX, double mouseY) {
        int x = leftPos + spec.x();
        int y = topPos + spec.y();
        return mouseX >= x && mouseX < x + PULSE_BOX_W && mouseY >= y && mouseY < y + PULSE_BOX_H;
    }

    /** Index of the readout under the cursor, or -1. */
    private int boxAt(double mouseX, double mouseY) {
        for (int i = 0; i < durationSpecs.size(); i++) {
            if (isOverBox(durationSpecs.get(i), mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        DurationPopup popup = openPopup();
        if (popup != null && popup.mouseClicked()) {
            return true;
        }
        if (button == 1) {
            int box = boxAt(mouseX, mouseY);
            if (box >= 0) {
                heldBox = box;
                heldTicks = 0;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        DurationPopup popup = openPopup();
        if (popup != null && popup.mouseReleased(button)) {
            return true;
        }
        if (button == 1) {
            heldBox = -1;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        DurationPopup popup = openPopup();
        if (popup != null && popup.mouseMoved(mouseX, mouseY)) {
            return;
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        DurationPopup popup = openPopup();
        if (popup != null && popup.mouseScrolled(scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
