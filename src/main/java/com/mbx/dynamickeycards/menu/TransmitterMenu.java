package com.mbx.dynamickeycards.menu;

import com.mbx.dynamickeycards.block.TransmitterBlockEntity;
import com.mbx.dynamickeycards.block.TransmitterMode;
import com.mbx.dynamickeycards.registry.DKMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * Sneak-opened mode UI for a transmitter: redstone-only vs. mixed, plus the manual trigger's
 * duration - see {@link AbstractDeviceModeMenu} for everything that isn't device-specific.
 */
public class TransmitterMenu extends AbstractDeviceModeMenu<TransmitterBlockEntity> {

    public TransmitterMenu(int containerId, Inventory playerInventory, TransmitterBlockEntity device) {
        super(DKMenuTypes.TRANSMITTER.get(), containerId, playerInventory, device);
    }

    /** Order must match {@code TransmitterScreen#modes()} - see {@link AbstractDeviceModeMenu}'s class doc. */
    @Override
    protected List<Runnable> modeSetters() {
        return List.of(
                () -> getDevice().setMode(TransmitterMode.REDSTONE_ONLY),
                () -> getDevice().setMode(TransmitterMode.MIXED));
    }

    @Override
    protected int durationTicks() {
        return getDevice().getManualTriggerTicks();
    }

    @Override
    protected void setDurationTicks(int ticks) {
        getDevice().setManualTriggerTicks(ticks);
    }

    /** Back to the vanilla stone-button default. */
    @Override
    protected void resetDurationTicks() {
        getDevice().resetManualTriggerTicks();
    }

    public static TransmitterMenu fromNetwork(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        BlockPos pos = extraData.readBlockPos();
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
        if (!(blockEntity instanceof TransmitterBlockEntity device)) {
            throw new IllegalStateException("No transmitter at " + pos);
        }
        return new TransmitterMenu(containerId, playerInventory, device);
    }
}
