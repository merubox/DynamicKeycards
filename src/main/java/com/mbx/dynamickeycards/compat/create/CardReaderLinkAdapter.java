package com.mbx.dynamickeycards.compat.create;

import com.mbx.dynamickeycards.block.CardReaderBlockEntity;
import com.mbx.dynamickeycards.block.CardReaderMode;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Wraps a card reader as a Create Redstone Link transmitter: the reader's own accept pulse
 * (see {@link CardReaderBlockEntity}) becomes the transmitted strength, keyed by whatever
 * items sit in the reader's two frequency slots (empty is a valid frequency, same as Create's
 * own Redstone Link). Never a receiver — this is a one-way broadcast, not a two-way link.
 *
 * <p>Only ever constructed through {@link CreateLinkCompat}, which keeps this class (and the
 * Create types it touches) unresolved unless Create is actually loaded.
 */
public class CardReaderLinkAdapter implements IRedstoneLinkable {

    private final CardReaderBlockEntity reader;

    CardReaderLinkAdapter(CardReaderBlockEntity reader) {
        this.reader = reader;
    }

    @Override
    public int getTransmittedStrength() {
        return reader.getBlockState().getValue(com.mbx.dynamickeycards.block.CardReaderBlock.MODE) == CardReaderMode.ACCEPTED
                ? 15 : 0;
    }

    @Override
    public void setReceivedStrength(int power) {
        // transmitter only — this reader never listens for an incoming signal
    }

    @Override
    public boolean isListening() {
        return false;
    }

    @Override
    public boolean isAlive() {
        Level level = reader.getLevel();
        BlockPos pos = reader.getBlockPos();
        return level != null && !reader.isRemoved() && level.isLoaded(pos)
                && level.getBlockEntity(pos) == reader;
    }

    @Override
    public Couple<Frequency> getNetworkKey() {
        return Couple.create(Frequency.of(reader.getFrequencySlot(0)), Frequency.of(reader.getFrequencySlot(1)));
    }

    @Override
    public BlockPos getLocation() {
        return reader.getBlockPos();
    }
}
