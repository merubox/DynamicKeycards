package com.mbx.dynamickeycards.menu;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.block.SirenBlockEntity;
import com.mbx.dynamickeycards.block.SirenMode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * Config screen for a siren: the three sound tiers, plus the turn speed and the release delay -
 * see {@link AbstractDeviceModeScreen} for the layout and behavior all of this is built on.
 *
 * <p>The only screen with two duration readouts, so it needs its own background: the boxes are
 * stacked in the panel at {@value #SPEED_BOX_Y}/{@value #DELAY_BOX_Y} instead of sitting in the
 * button row, and that art is baked into {@link #BACKGROUND} rather than drawn here.
 */
public class SirenScreen extends AbstractDeviceModeScreen<SirenMenu> {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, "textures/gui/siren_mode.png");

    private static final int BOX_X = 70;
    private static final int SPEED_BOX_Y = 24;
    private static final int DELAY_BOX_Y = 42;

    /**
     * The plain tick row, from {@link SirenBlockEntity#MIN_ROTATION_TICKS} to
     * {@link SirenBlockEntity#MAX_ROTATION_TICKS} in steps of the former - the cursor skips the
     * ticks in between, since a period that isn't a multiple of 4 can't split evenly into the
     * eight frames. Only the one row: the seconds and minutes rows would offer nothing but values
     * past the ceiling.
     *
     * <p>Otherwise it is the standard tick row, milestones and all - the same ruler every other
     * picker in the mod draws, so the only thing that reads as different here is which columns the
     * cursor will stop on.
     */
    private static final DurationPopup.Scale TURN_SPEED = new DurationPopup.Scale(
            List.of(new DurationPopup.Row("ticks", 1, SirenScreen::formatTicks)),
            SirenBlockEntity.MIN_ROTATION_TICKS, SirenBlockEntity.MAX_ROTATION_TICKS,
            10, SirenBlockEntity.MIN_ROTATION_TICKS);

    public SirenScreen(SirenMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected BlockEntity device() {
        return menu.getDevice();
    }

    @Override
    protected String langNamespace() {
        return "siren";
    }

    @Override
    protected ResourceLocation background() {
        return BACKGROUND;
    }

    /** Order must match {@code SirenMenu#modeSetters()} - the index is the button id. */
    @Override
    protected List<ModeSpec> modes() {
        return List.of(
                new ModeSpec(ModeButton.ICON_MUTE, "mute_mode",
                        () -> menu.getDevice().getMode() == SirenMode.MUTE),
                new ModeSpec(ModeButton.ICON_CAUTION, "caution_mode",
                        () -> menu.getDevice().getMode() == SirenMode.CAUTION),
                new ModeSpec(ModeButton.ICON_EMERGENCY, "emergency_mode",
                        () -> menu.getDevice().getMode() == SirenMode.EMERGENCY));
    }

    /** Order must match {@code SirenMenu#durations()} - the index is the button id's range. */
    @Override
    protected List<DurationSpec> durations() {
        return List.of(
                new DurationSpec(BOX_X, SPEED_BOX_Y, "rotation_speed",
                        () -> menu.getDevice().getRotationPeriodTicks(),
                        SirenScreen::formatTicks, TURN_SPEED),
                new DurationSpec(BOX_X, DELAY_BOX_Y, "release_delay",
                        () -> menu.getDevice().getReleaseDelayTicks(),
                        AbstractDeviceModeScreen::formatTicksCompact, DurationPopup.TICKS_SECONDS_MINUTES));
    }

    /**
     * Ticks, always - not {@code formatTicksCompact}, which would switch the round multiples of
     * 20 over to seconds and leave the readout flipping between units as the speed is scrubbed.
     */
    private static String formatTicks(int ticks) {
        return ticks + "t";
    }
}
