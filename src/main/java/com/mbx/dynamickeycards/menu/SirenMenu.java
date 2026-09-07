package com.mbx.dynamickeycards.menu;

import com.mbx.dynamickeycards.block.SirenBlockEntity;
import com.mbx.dynamickeycards.block.SirenMode;
import com.mbx.dynamickeycards.registry.DKMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * Wrench-opened config UI for a siren: the three sound tiers, plus the two durations that shape
 * the sweep - see {@link AbstractDeviceModeMenu} for everything that isn't device-specific.
 *
 * <p>The first device here with more than one duration, which is what pushed
 * {@link AbstractDeviceModeMenu} from a single setting to a list.
 */
public class SirenMenu extends AbstractDeviceModeMenu<SirenBlockEntity> {

    public SirenMenu(int containerId, Inventory playerInventory, SirenBlockEntity device) {
        super(DKMenuTypes.SIREN.get(), containerId, playerInventory, device);
    }

    /** Order must match {@code SirenScreen#modes()} - see {@link AbstractDeviceModeMenu}'s class doc. */
    @Override
    protected List<Runnable> modeSetters() {
        return List.of(
                () -> getDevice().setMode(SirenMode.MUTE),
                () -> getDevice().setMode(SirenMode.CAUTION),
                () -> getDevice().setMode(SirenMode.EMERGENCY));
    }

    /** Turn speed first, then release delay - the order the two boxes are stacked in on the screen. */
    @Override
    protected List<DurationSetting> durations() {
        return List.of(
                new DurationSetting(
                        () -> getDevice().getRotationPeriodTicks(),
                        ticks -> getDevice().setRotationPeriodTicks(ticks),
                        () -> getDevice().resetRotationPeriodTicks()),
                new DurationSetting(
                        () -> getDevice().getReleaseDelayTicks(),
                        ticks -> getDevice().setReleaseDelayTicks(ticks),
                        () -> getDevice().resetReleaseDelayTicks()));
    }

    public static SirenMenu fromNetwork(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        BlockPos pos = extraData.readBlockPos();
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
        if (!(blockEntity instanceof SirenBlockEntity device)) {
            throw new IllegalStateException("No siren at " + pos);
        }
        return new SirenMenu(containerId, playerInventory, device);
    }
}
