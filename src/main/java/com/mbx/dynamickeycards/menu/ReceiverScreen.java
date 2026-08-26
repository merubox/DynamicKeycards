package com.mbx.dynamickeycards.menu;

import com.mbx.dynamickeycards.block.ReceiverMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * Config screen for a receiver: pulse vs. toggle mode, plus the release delay - see
 * {@link AbstractDeviceModeScreen} for the layout and behavior all of this is built on.
 *
 * <p>The release delay stays editable in either mode, matching {@code LinkDeviceScreen}'s own
 * signal-length box (live in every {@code SignalMode}); it simply has no effect in
 * {@link ReceiverMode#TOGGLE}, which never reads it.
 */
public class ReceiverScreen extends AbstractDeviceModeScreen<ReceiverMenu> {

    public ReceiverScreen(ReceiverMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected BlockEntity device() {
        return menu.getDevice();
    }

    @Override
    protected String langNamespace() {
        return "receiver";
    }

    /** Order must match {@code ReceiverMenu#modeSetters()} - the index is the button id. */
    @Override
    protected List<ModeSpec> modes() {
        return List.of(
                new ModeSpec(ModeButton.ICON_BUTTON, "pulse_mode",
                        () -> menu.getDevice().getMode() == ReceiverMode.PULSE),
                new ModeSpec(ModeButton.ICON_LEVER, "toggle_mode",
                        () -> menu.getDevice().getMode() == ReceiverMode.TOGGLE));
    }

    @Override
    protected String durationKey() {
        return "release_delay";
    }

    @Override
    protected int durationTicks() {
        return menu.getDevice().getReleaseDelayTicks();
    }
}
