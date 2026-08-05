package com.mbx.dynamickeycards.compat.create;

import com.mbx.dynamickeycards.block.LinkDeviceBlockEntity;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Wraps any {@link LinkDeviceBlockEntity} (the card reader, or a motion sensor) as a Create
 * Redstone Link transmitter, keyed by whatever items sit in its two frequency slots (empty is a
 * valid frequency, same as Create's own Redstone Link). Never a receiver — this is a one-way
 * broadcast, not a two-way link.
 *
 * <p>Only ever constructed through {@link CreateLinkCompat}, which keeps this class (and the
 * Create types it touches) unresolved unless Create is actually loaded.
 */
public class LinkAdapter implements IRedstoneLinkable {

    private final LinkDeviceBlockEntity device;

    LinkAdapter(LinkDeviceBlockEntity device) {
        this.device = device;
    }

    @Override
    public int getTransmittedStrength() {
        return device.getLinkStrength();
    }

    @Override
    public void setReceivedStrength(int power) {
        // transmitter only — this device never listens for an incoming signal
    }

    @Override
    public boolean isListening() {
        return false;
    }

    @Override
    public boolean isAlive() {
        Level level = device.getLevel();
        BlockPos pos = device.getBlockPos();
        return level != null && !device.isRemoved() && level.isLoaded(pos)
                && level.getBlockEntity(pos) == device;
    }

    @Override
    public Couple<Frequency> getNetworkKey() {
        return Couple.create(Frequency.of(device.getFrequencySlot(0)), Frequency.of(device.getFrequencySlot(1)));
    }

    @Override
    public BlockPos getLocation() {
        return device.getBlockPos();
    }
}
