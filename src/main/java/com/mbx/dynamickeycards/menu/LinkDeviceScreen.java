package com.mbx.dynamickeycards.menu;

import com.mbx.dynamickeycards.DKTooltips;
import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.block.AdvancedSensorBlockEntity;
import com.mbx.dynamickeycards.block.BoundReaderMode;
import com.mbx.dynamickeycards.block.CardReaderBlockEntity;
import com.mbx.dynamickeycards.block.LinkDeviceBlockEntity;
import com.mbx.dynamickeycards.block.SignalMode;
import com.mbx.dynamickeycards.compat.create.CreateAvailability;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrench-opened config screen shared by every {@code LinkDeviceBlockEntity}: background + two
 * ghost frequency slots from {@link LinkDeviceMenu},
 * plus five buttons (normal mode / link mode / mixed mode / reset / confirm) drawn from
 * {@code broadcast_widgets.png} (button-box states) and {@code broadcast_icons.png} (icon
 * glyphs).
 *
 * <p>Main panel is 184x99 with ghost slots at (80,25)/(80,43) and buttons on one row at
 * y=75 — normal (7), link (25) and mixed (43) flush against each other, reset (122),
 * confirm (151) — then a separate player-inventory panel ({@code broadcast_player_inventory.png}) below it at
 * local x=30, y = BG_HEIGHT+4; slots at {@code invX+8+col*18, invY+18+row*18} (hotbar
 * {@code invY+76}) — see {@link LinkDeviceMenu} for the matching slot coordinates.
 * {@code imageHeight} includes the inventory panel's height too, so the whole window (not just
 * the main panel) is what gets centered on screen. {@code leftPos}/{@code topPos} get a small
 * (-11, 5) nudge applied after centering to land at the intended on-screen position.
 *
 * <p>To the right of the arrow, a scaled-up render of the device's own item (whichever block
 * this menu currently belongs to - reader, wall sensor, or ceiling sensor) sits next to the
 * arrow as a block preview. Button tooltips follow this mod's existing
 * {@link DKTooltips#summary} convention (name only, "Hold [Shift]" hint, full description while
 * Shift is held) instead of vanilla's plain {@code Tooltip}, and the title is drawn manually in
 * {@code TITLE_COLOR} rather than vanilla's default.
 *
 * <p>The number box on the button row (local 70,75 - 105,92) shows the device's current signal
 * length - what that actually controls is up to the device (see {@code LinkDeviceBlockEntity}).
 * Holding right-click on it for a few ticks opens a full-screen value-adjustment overlay: three
 * horizontal scales (ticks / seconds / minutes) with milestone marks every 10 units; moving the
 * mouse (or scrolling) picks the row closest to the cursor's height and the value closest to its
 * position along that row, and releasing right-click confirms whatever is currently highlighted
 * and sends it to the server. Shift snaps movement to whole milestones.
 */
public class LinkDeviceScreen extends AbstractContainerScreen<LinkDeviceMenu> {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/broadcast_mode.png");
    private static final ResourceLocation WIDGETS =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/broadcast_widgets.png");
    private static final ResourceLocation ICONS =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/broadcast_icons.png");
    /**
     * The two frequency ghost slots (red/blue, stacked, 18x18 each) plus the 1px gray frame
     * wrapping the pair (20x38 total) - drawn separately from {@link #BACKGROUND} rather than
     * baked into it, unlike the old art. {@link #BACKGROUND} is shared with
     * {@code TransmitterScreen}/{@code ReceiverScreen}, neither of which has any slots at all, so
     * keeping this out of the shared background avoids drawing dead pixels there. Always visible
     * regardless of whether a slot is occupied - see the two ghost slots' own position in
     * {@code LinkDeviceMenu} ((80,25)/(80,43)) for where these line up: a vanilla slot's
     * background occupies one pixel above/left of its own coordinate, and the frame sits one
     * more pixel out from that on every side.
     */
    private static final ResourceLocation SLOTS =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/broadcast_slots.png");
    private static final ResourceLocation PLAYER_INVENTORY =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/broadcast_player_inventory.png");

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
    /**
     * Not one of our own sounds - looked up by id at runtime, so this only ever plays when
     * Create happens to be installed (the lookup is simply absent otherwise). No Create class
     * is referenced anywhere for this, so it carries none of the usual optional-dependency risk.
     */

    private ModeButton normalModeButton;
    private ModeButton linkModeButton;
    private ModeButton simultaneousModeButton;

    private int pulseBoxHeldTicks = -1;
    private final DurationPopup durationPopup = new DurationPopup(this,
            () -> menu.getDevice().getSignalLength(),
            ticks -> sendButtonClick(LinkDeviceMenu.SIGNAL_LENGTH_ID_BASE + ticks),
            () -> Component.translatable("dynamickeycards.link_device.signal_length"));

    public LinkDeviceScreen(LinkDeviceMenu menu, Inventory playerInventory, Component title) {
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

        normalModeButton = addRenderableWidget(new ModeButton(leftPos + 7, topPos + BUTTON_Y, ModeButton.ICON_REDSTONE,
                "normal_mode", () -> sendButtonClick(LinkDeviceMenu.BUTTON_NORMAL_MODE)));

        linkModeButton = addRenderableWidget(new ModeButton(leftPos + 25, topPos + BUTTON_Y, ModeButton.ICON_LINK,
                "link_mode", () -> sendButtonClick(LinkDeviceMenu.BUTTON_LINK_MODE)));

        simultaneousModeButton = addRenderableWidget(new ModeButton(leftPos + 43, topPos + BUTTON_Y, ModeButton.ICON_SIMULTANEOUS,
                "simultaneous_mode", () -> sendButtonClick(LinkDeviceMenu.BUTTON_SIMULTANEOUS_MODE)));

        // no tooltip on these two - icon-obvious enough, so titleKey is unused (null)
        addRenderableWidget(new ModeButton(leftPos + 122, topPos + BUTTON_Y, ModeButton.ICON_RESET,
                null, () -> sendButtonClick(LinkDeviceMenu.BUTTON_RESET)));

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
        durationPopup.tick();
    }

    /**
     * Also picks each button's {@code titleKey} for the current device/binding state - a sensor
     * bound to a reader repurposes the three buttons to its own {@code BoundReaderMode} instead
     * of {@link SignalMode} (still freely clickable there, unlike a sensor bound to *another
     * sensor*, which stays locked to whatever that sensor owns - see
     * {@code LinkDeviceBlockEntity#isLinkModeEditable}'s own doc), and a plain reader vs. an
     * unbound sensor use different wording for the same underlying {@link SignalMode#NORMAL}.
     */
    private void updateModeIndicators() {
        LinkDeviceBlockEntity device = menu.getDevice();
        if (device instanceof AdvancedSensorBlockEntity sensor && sensor.getBoundReader() != null) {
            BoundReaderMode mode = sensor.getBoundReaderMode();
            normalModeButton.titleKey = "bound_sensor_centric_mode";
            linkModeButton.titleKey = "bound_reader_only_mode";
            simultaneousModeButton.titleKey = "bound_simultaneous_mode";
            normalModeButton.green = mode == BoundReaderMode.SENSOR_CENTRIC_SIMULTANEOUS;
            linkModeButton.green = mode == BoundReaderMode.READER_ONLY;
            simultaneousModeButton.green = mode == BoundReaderMode.SIMULTANEOUS;
            normalModeButton.active = true;
            linkModeButton.active = true;
            simultaneousModeButton.active = true;
            return;
        }

        normalModeButton.titleKey = device instanceof CardReaderBlockEntity ? "reader_only_mode" : "sensor_only_mode";
        linkModeButton.titleKey = "link_only_mode";
        simultaneousModeButton.titleKey = "simultaneous_mode";

        SignalMode mode = device.getSignalMode();
        normalModeButton.green = mode == SignalMode.NORMAL;
        linkModeButton.green = mode == SignalMode.LINK;
        simultaneousModeButton.green = mode == SignalMode.SIMULTANEOUS;

        // a sensor bound to *another sensor* already has its actual mode/frequency owned by that
        // sensor - lock these out (dimmed, clicks ignored) rather than let them lie
        boolean editable = device.isLinkModeEditable();
        // LINK/SIMULTANEOUS both route redstone over Create's Redstone Link (see SignalMode#linkActive) -
        // locking them out the same "dimmed, clicks ignored" way whenever Create isn't installed,
        // rather than leaving them clickable into a mode that can never actually take effect
        boolean createAvailable = CreateAvailability.isLoaded();
        normalModeButton.active = editable;
        linkModeButton.active = editable && createAvailable;
        simultaneousModeButton.active = editable && createAvailable;
    }

    /**
     * Same dimmed-lock treatment as the mode buttons, for the two ghost frequency slots - only
     * while Create is installed and they're merely locked (a bound advanced sensor doesn't own its
     * own frequency). With Create absent there's no slot graphic underneath to dim in the first
     * place - see {@link #renderBg} - so this stays out of the way entirely rather than drawing a
     * translucent rectangle over nothing.
     */
    private void renderLockedFrequencySlots(GuiGraphics graphics) {
        if (!CreateAvailability.isLoaded() || menu.getDevice().isFrequencyEditable()) {
            return;
        }
        graphics.fill(leftPos + 79, topPos + 24, leftPos + 97, topPos + 42, 0x90000000);
        graphics.fill(leftPos + 79, topPos + 42, leftPos + 97, topPos + 60, 0x90000000);
    }

    private void tickPulseBoxHold() {
        if (durationPopup.isOpen() || pulseBoxHeldTicks < 0) {
            return;
        }
        if (pulseBoxHeldTicks++ >= PULSE_HOLD_OPEN_TICKS) {
            durationPopup.open();
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
        // the two ghost frequency slots are a Create Redstone Link key and mean nothing without
        // it - LinkDeviceMenu doesn't even add them as real Slots in that case (see its own doc),
        // so there's nothing here to click on either way; just skip drawing the frame around them
        if (CreateAvailability.isLoaded()) {
            graphics.blit(SLOTS, leftPos + 78, topPos + 23, 0, 0, 20, 38, 20, 38);
        }

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
        int ticks = menu.getDevice().getSignalLength();
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
     * Base Z for {@link #renderDeviceIcon}. {@code GuiGraphics#renderItem} adds 150 of its own Z,
     * and the uniform 4x scale below multiplies that to 600 - so this offset is what lands the
     * finished icon at Z 200: above the background panel (Z 0), but below the Z 400 plane vanilla
     * draws tooltips on. Left at the old Z 100 the icon ended up at Z 700 and painted itself over
     * every tooltip that reached it. The scale has to stay uniform (the block model is rotated in
     * its GUI transform, so scaling Z differently from X/Y would shear it) - which is exactly why
     * this is corrected here on the base offset instead.
     */
    private static final int ICON_Z = -400;

    /**
     * A scaled-up icon of whichever device block this screen belongs to, rendered next to the
     * arrow (every reader variant and both motion sensors share this one screen, so the icon
     * has to follow the instance).
     */
    private void renderDeviceIcon(GuiGraphics graphics) {
        ItemStack stack = new ItemStack(menu.getDevice().getBlockState().getBlock().asItem());
        int x = getIconAreaX();
        int y = getIconAreaY();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, ICON_Z);
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
        return durationPopup.isOpen() ? new int[] {0, 0, this.width, this.height} : null;
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
        if (durationPopup.isOpen()) {
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderLockedFrequencySlots(graphics);
        renderTooltip(graphics, mouseX, mouseY);
        renderButtonTooltip(graphics, normalModeButton, mouseX, mouseY);
        renderButtonTooltip(graphics, linkModeButton, mouseX, mouseY);
        renderButtonTooltip(graphics, simultaneousModeButton, mouseX, mouseY);
        renderEmptySlotTooltip(graphics, mouseX, mouseY);
        renderPulseBoxTooltip(graphics, mouseX, mouseY);
        durationPopup.render(graphics);
    }

    /**
     * Same "name + Hold [Shift] for Summary" convention as the mod's item tooltips. Reset/confirm
     * are icon-obvious enough that they get no tooltip at all (not even a name).
     * Checks a live {@code isMouseOver} rather than {@code isHoveredOrFocused} - focus lingers
     * after a click, which was leaving the tooltip stuck on screen after pressing a button.
     */
    private void renderButtonTooltip(GuiGraphics graphics, ModeButton button, int mouseX, int mouseY) {
        if (durationPopup.isOpen() || !button.isMouseOver(mouseX, mouseY)) {
            return;
        }
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("dynamickeycards.card_reader." + button.titleKey));
        DKTooltips.summary(tooltip, button.titleKey + "1");
        graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    /**
     * An empty frequency slot shows its own name (Frequency #1/#2) as a hover tooltip - but not
     * while the slots are locked (see {@link #renderLockedFrequencySlots}): a bound advanced
     * sensor doesn't own its frequency anymore, so naming a slot you can't edit is just noise.
     * Also skipped entirely without Create: {@code menu.getSlot(0)}/{@code (1)} then refer to the
     * player's own first two inventory slots instead (see {@code LinkDeviceMenu}'s own doc on why
     * the ghost slots simply aren't added there) - labeling those "Frequency #1/#2" would be a
     * real bug, not just noise.
     */
    private void renderEmptySlotTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (durationPopup.isOpen() || hoveredSlot == null || hoveredSlot.hasItem()
                || !CreateAvailability.isLoaded() || !menu.getDevice().isFrequencyEditable()) {
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
        if (durationPopup.isOpen() || !isOverPulseBox(mouseX, mouseY)) {
            return;
        }
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("dynamickeycards.link_device.signal_length"));
        tooltip.add(Component.translatable("dynamickeycards.link_device.signal_length.hold_to_edit",
                        Component.keybind("key.use"))
                .withStyle(ChatFormatting.GRAY));
        graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    /** The signal-length readout in the button row - held with right-click to open {@link #durationPopup}. */
    private boolean isOverPulseBox(double mouseX, double mouseY) {
        int x = leftPos + PULSE_BOX_X;
        int y = topPos + BUTTON_Y;
        return mouseX >= x && mouseX < x + PULSE_BOX_W && mouseY >= y && mouseY < y + PULSE_BOX_H;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (durationPopup.mouseClicked()) {
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
        if (durationPopup.mouseReleased(button)) {
            return true;
        }
        if (button == 1) {
            pulseBoxHeldTicks = -1;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (durationPopup.mouseMoved(mouseX, mouseY)) {
            return;
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (durationPopup.mouseScrolled(scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

}
