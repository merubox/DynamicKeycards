package com.mbx.dynamickeycards.menu;

import com.mbx.dynamickeycards.block.ReceiverBlockEntity;
import com.mbx.dynamickeycards.block.ReceiverMode;
import com.mbx.dynamickeycards.registry.DKMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * Sneak-opened mode UI for a receiver: pulse vs. toggle, plus the release delay - see
 * {@link AbstractDeviceModeMenu} for everything that isn't device-specific.
 */
public class ReceiverMenu extends AbstractDeviceModeMenu<ReceiverBlockEntity> {

    public ReceiverMenu(int containerId, Inventory playerInventory, ReceiverBlockEntity device) {
        super(DKMenuTypes.RECEIVER.get(), containerId, playerInventory, device);
    }

    /** Order must match {@code ReceiverScreen#modes()} - see {@link AbstractDeviceModeMenu}'s class doc. */
    @Override
    protected List<Runnable> modeSetters() {
        return List.of(
                () -> getDevice().setMode(ReceiverMode.PULSE),
                () -> getDevice().setMode(ReceiverMode.TOGGLE));
    }

    /** Just the release delay, whose default is 0 - an instant cutoff. */
    @Override
    protected List<DurationSetting> durations() {
        return List.of(new DurationSetting(
                () -> getDevice().getReleaseDelayTicks(),
                ticks -> getDevice().setReleaseDelayTicks(ticks),
                () -> getDevice().resetReleaseDelayTicks()));
    }

    public static ReceiverMenu fromNetwork(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        BlockPos pos = extraData.readBlockPos();
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
        if (!(blockEntity instanceof ReceiverBlockEntity device)) {
            throw new IllegalStateException("No receiver at " + pos);
        }
        return new ReceiverMenu(containerId, playerInventory, device);
    }
}
