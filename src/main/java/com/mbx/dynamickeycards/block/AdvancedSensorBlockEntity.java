package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DKSounds;
import com.mbx.dynamickeycards.registry.DKBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A strict superset of {@link MotionSensorBlockEntity}: unbound, it behaves exactly like the
 * plain motion sensor (inherited {@link MotionSensorBlockEntity#tick} - same UI, same detection,
 * same redstone/link output). Bound to a card reader instead (see {@code BoundSensorBlockItem}
 * for how that happens), it stops detecting entities for its own signal and instead checks
 * whether any nearby player is carrying a card the bound reader would accept - if so, it drives
 * that reader's own accept pulse directly ({@link CardReaderBlock#acceptPulse}/
 * {@link CardReaderBlock#releasePulse}), sound/light/redstone and all, as if physically tapped.
 *
 * <p>While bound, the mode/frequency slots are locked to whatever the reader already has (see
 * {@link #isLinkModeEditable}) - only this sensor's own signal length keeps its ordinary meaning
 * (here, how long to keep the reader triggered after the last valid card-holder leaves), since a
 * proximity sensor and a tap reader are different enough in how they get used that they
 * shouldn't be forced to share one timing knob. Being bound doesn't stop this from also being a
 * sensor in its own right: its own local redstone output ({@code PRESENT}, around its own
 * position) still fires alongside the reader's, on the same detection - there's no way yet to
 * route it to just one side, that's left for a possible later config UI.
 *
 * <p>Right-clicking a placed one with a dye sets {@link #getAccentColor}, purely cosmetic (see
 * {@code AdvancedWallSensorBlock}/{@code AdvancedCeilingSensorBlock}'s {@code useItemOn} for the
 * interaction, and {@code AdvancedSensorRenderer} for how it's drawn); a gold nugget clears it
 * back to the native undyed look.
 */
public class AdvancedSensorBlockEntity extends MotionSensorBlockEntity {

    @Nullable
    private BlockPos boundReaderPos;
    /** Bound-mode equivalent of the parent's own last-detected tracking, kept separate since only one mode is ever active. */
    private long lastCardDetectedGameTime = -1;
    /**
     * Whether we're the one currently holding the reader's pulse open. Without this, a player
     * physically tapping the bound reader directly - while standing outside this sensor's own
     * detection zone - got their pulse cut short on the very next tick: {@code shouldTrigger} was
     * false (we never saw them), but the reader still read as ACCEPTED (from their own tap), so
     * the old code released it immediately regardless of who caused it, closing whatever the
     * signal was driving before the player could walk through. Not persisted - a pulse never
     * survives a reload anyway, and defaulting to false on load just means we won't release a
     * pulse we didn't start, which is the safe direction to be wrong in.
     */
    private boolean weTriggeredPulse;
    /** {@code null} = its native undyed look; see {@code AdvancedSensorDyeing}. */
    @Nullable
    private DyeColor accentColor;

    public AdvancedSensorBlockEntity(BlockPos pos, BlockState state) {
        super(DKBlockEntities.ADVANCED_SENSOR.get(), pos, state);
    }

    @Nullable
    public DyeColor getAccentColor() {
        return accentColor;
    }

    public void setAccentColor(@Nullable DyeColor color) {
        this.accentColor = color;
        this.syncToClient();
    }

    public void setBoundReader(@Nullable BlockPos readerPos) {
        this.boundReaderPos = readerPos;
        if (readerPos != null) {
            // becoming bound: the reader owns the real mode/frequency now, so force back to
            // NORMAL (and off any Create Link registration of its own) - harmless if already there
            super.setSignalMode(SignalMode.NORMAL);
        }
        this.setChanged();
    }

    @Nullable
    public BlockPos getBoundReader() {
        return boundReaderPos;
    }

    /**
     * Server-side only: called from each advanced sensor block's {@code setPlacedBy} right after
     * {@link #setBoundReader}. Green, unlike the white "tuned" message shown when the item was
     * bound (see {@code CardReaderBlock#useItemOn}) - that step only tuned the held item, this is
     * the point the connection actually exists in the world.
     */
    static void announcePlaced(Level level, BlockPos pos, @Nullable LivingEntity placer, AdvancedSensorBlockEntity sensor) {
        if (sensor.boundReaderPos == null || !(placer instanceof Player player)) {
            return;
        }
        player.displayClientMessage(
                Component.translatable("dynamickeycards.link_device.linked").withStyle(ChatFormatting.GREEN), true);
        DKSounds.confirm(level, pos);
    }

    @Override
    public boolean isLinkModeEditable() {
        return boundReaderPos == null;
    }

    @Override
    public void setSignalMode(SignalMode mode) {
        if (isLinkModeEditable()) {
            super.setSignalMode(mode);
        }
    }

    @Override
    public void setFrequencySlot(int index, ItemStack stack) {
        if (isLinkModeEditable()) {
            super.setFrequencySlot(index, stack);
        }
    }

    /** Same column as {@link MotionSensorBlock#detectionZone} - this block's own cell and the one below it. */
    private static AABB detectionZone(BlockPos pos) {
        return new AABB(pos.getX(), pos.getY() - 1, pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
    }

    private static boolean carriesAcceptedCard(Player player, CardReaderBlockEntity reader) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && reader.accepts(stack)) {
                return true;
            }
        }
        return false;
    }

    static void tick(Level level, BlockPos pos, BlockState state, AdvancedSensorBlockEntity be) {
        BlockPos readerPos = be.boundReaderPos;
        if (readerPos == null) {
            // standalone: no different from a plain motion sensor
            MotionSensorBlockEntity.tick(level, pos, state, be);
            return;
        }
        if (!(level.getBlockEntity(readerPos) instanceof CardReaderBlockEntity reader)) {
            return;
        }
        boolean detected = !level.getEntitiesOfClass(Player.class, detectionZone(pos),
                player -> !player.isSpectator() && carriesAcceptedCard(player, reader)).isEmpty();
        long now = level.getGameTime();
        if (detected) {
            be.lastCardDetectedGameTime = now;
        }
        int hold = be.getSignalLength();
        boolean shouldTrigger = detected || (hold > 0 && be.lastCardDetectedGameTime >= 0
                && now - be.lastCardDetectedGameTime < hold);

        // the sensor's own local redstone output fires right alongside the reader's - being
        // bound doesn't stop it from being a sensor in its own right at its own position
        applyPresent(level, pos, state, be, shouldTrigger);

        BlockState readerState = level.getBlockState(readerPos);
        if (!(readerState.getBlock() instanceof CardReaderBlock readerBlock)) {
            return;
        }
        boolean currentlyAccepted = readerState.getValue(CardReaderBlock.MODE) == CardReaderMode.ACCEPTED;
        if (!currentlyAccepted) {
            // whatever pulse was active (ours or someone else's, e.g. a direct tap) is over
            be.weTriggeredPulse = false;
        }
        if (shouldTrigger) {
            if (!currentlyAccepted) {
                // rising edge: trigger the reader exactly like a physical tap, and take
                // responsibility for releasing it - see weTriggeredPulse's own doc
                readerBlock.acceptPulse(readerState, level, readerPos, null);
                be.weTriggeredPulse = true;
            } else {
                // already accepted - keep the pulse alive without repeating the accept effects.
                // Deliberately does *not* set weTriggeredPulse: if this pulse started some other
                // way (a direct tap on the reader), that pulse is running on the reader's own
                // configured length and isn't ours to cut short just because our own hold delay
                // happens to lapse first - only a pulse *we* actually started gets released early.
                reader.onPulseStarted();
            }
        } else if (currentlyAccepted && be.weTriggeredPulse) {
            // our own hold delay expired and we're the one who started this pulse - release
            // right away rather than waiting on whatever the reader's own (unrelated) pulse
            // length happens to be.
            readerBlock.releasePulse(readerState, level, readerPos);
            be.weTriggeredPulse = false;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (boundReaderPos != null) {
            tag.put("BoundReader", NbtUtils.writeBlockPos(boundReaderPos));
        }
        if (accentColor != null) {
            tag.putString("AccentColor", accentColor.getSerializedName());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boundReaderPos = tag.contains("BoundReader") ? NbtUtils.readBlockPos(tag, "BoundReader").orElse(null) : null;
        accentColor = tag.contains("AccentColor") ? DyeColor.byName(tag.getString("AccentColor"), null) : null;
    }
}
