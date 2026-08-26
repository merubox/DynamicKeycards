package com.mbx.dynamickeycards.menu;

import com.mbx.dynamickeycards.block.TransmitterMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * Config screen for a transmitter: redstone-only vs. manual-mixed mode, plus the manual trigger's
 * duration - see {@link AbstractDeviceModeScreen} for the layout and behavior all of this is
 * built on.
 *
 * <p>The duration only has anything to act on in {@link TransmitterMode#MIXED}; a
 * redstone-only transmitter takes its pulse length from the redstone signal reaching it instead.
 */
public class TransmitterScreen extends AbstractDeviceModeScreen<TransmitterMenu> {

    public TransmitterScreen(TransmitterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected BlockEntity device() {
        return menu.getDevice();
    }

    @Override
    protected String langNamespace() {
        return "transmitter";
    }

    /** Order must match {@code TransmitterMenu#modeSetters()} - the index is the button id. */
    @Override
    protected List<ModeSpec> modes() {
        return List.of(
                new ModeSpec(ModeButton.ICON_REDSTONE, "redstone_only_mode",
                        () -> menu.getDevice().getMode() == TransmitterMode.REDSTONE_ONLY),
                new ModeSpec(ModeButton.ICON_MANUAL, "mixed_mode",
                        () -> menu.getDevice().getMode() == TransmitterMode.MIXED));
    }

    @Override
    protected String durationKey() {
        return "manual_trigger_ticks";
    }

    @Override
    protected int durationTicks() {
        return menu.getDevice().getManualTriggerTicks();
    }
}
