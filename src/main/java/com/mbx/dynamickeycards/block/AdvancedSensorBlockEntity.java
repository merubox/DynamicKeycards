package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.DKSounds;
import com.mbx.dynamickeycards.item.BoundSensorBlockItem;
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
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * A strict superset of {@link MotionSensorBlockEntity}: unbound, it behaves exactly like the
 * plain motion sensor (inherited {@link MotionSensorBlockEntity#tick} - same UI, same detection,
 * same redstone/link output). It can instead be bound to exactly one of two kinds of target
 * (see {@code BoundSensorBlockItem} for how that's set up before placement):
 * <ul>
 *   <li><b>A card reader</b> - it stops detecting entities for its own signal and instead checks
 *   whether any nearby player is carrying a card the bound reader would accept; if so, it drives
 *   that reader's own accept pulse directly ({@link CardReaderBlock#acceptPulse}), sound/light/
 *   redstone and all, as if physically tapped.</li>
 *   <li><b>Another sensor</b> (plain or advanced) - it keeps its own ordinary zone detection, but
 *   also relays that same on/off pattern (release delay included) into the target's own signal
 *   remotely, via {@link MotionSensorBlockEntity#holdExternalSignal}. If the target is itself an
 *   advanced sensor, the binding is set up mutually on placement (see
 *   {@link #applyPlacedBinding}), so each one drives the other; a plain target only ever gets
 *   driven, never drives back - that asymmetry is why only advanced sensors carry this item's
 *   binding capability in the first place.</li>
 * </ul>
 *
 * <p>While bound (either kind), the mode/frequency slots are locked to whatever the target
 * already has (see {@link #isLinkModeEditable}) - only this sensor's own signal length keeps its
 * ordinary meaning (how long to keep driving the target after the last trigger condition ends),
 * since the two devices are different enough in how they get used that they shouldn't be forced
 * to share one timing knob. Being bound doesn't stop this from also being a sensor in its own
 * right: its own local redstone output ({@code PRESENT}, around its own position) still fires
 * alongside whatever it's driving, on the same detection.
 *
 * <p>Right-clicking a placed one with a dye sets {@link #getAccentColor}, purely cosmetic (see
 * {@code AdvancedWallSensorBlock}/{@code AdvancedCeilingSensorBlock}'s {@code useItemOn} for the
 * interaction, and {@code AdvancedSensorRenderer} for how it's drawn); a gold nugget clears it
 * back to the native undyed look.
 */
public class AdvancedSensorBlockEntity extends MotionSensorBlockEntity {

    /**
     * How far ahead of "now" each tick's {@link MotionSensorBlockEntity#holdExternalSignal}/
     * {@link CardReaderBlockEntity#holdExternalSignal} call pushes the hold deadline. 1 tick of
     * margin wouldn't be enough: whether a driven device's own ticker observes "still held" on a
     * given game tick would then depend on whether *this* sensor's tick (for that same game
     * tick) happened to run first - the same tick-ordering hazard that caused the old
     * double-release bug this mechanism replaces. 2 guarantees the deadline set on the
     * *previous* tick alone already covers the current one, regardless of ordering.
     */
    private static final int HOLD_BUFFER_TICKS = 2;

    @Nullable
    private BlockPos boundReaderPos;
    @Nullable
    private BlockPos boundSensorPos;
    /** Bound-mode equivalent of the parent's own last-detected tracking, kept separate since only one mode is ever active. */
    private long lastCardDetectedGameTime = -1;
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
            // bound to exactly one thing at a time - see the class doc
            this.boundSensorPos = null;
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

    public void setBoundSensor(@Nullable BlockPos sensorPos) {
        this.boundSensorPos = sensorPos;
        if (sensorPos != null) {
            // bound to exactly one thing at a time - see the class doc
            this.boundReaderPos = null;
            super.setSignalMode(SignalMode.NORMAL);
        }
        this.setChanged();
    }

    @Nullable
    public BlockPos getBoundSensor() {
        return boundSensorPos;
    }

    /**
     * Rewrites whichever of {@link #boundReaderPos}/{@link #boundSensorPos} is set through
     * {@code transform} - called from {@code compat.create.PositionTransformCompat} when this
     * sensor is moved as a whole (a Create schematic printed at an offset/rotation, or a
     * contraption disassembling elsewhere). See {@code CardReaderBlockEntity#applyPositionTransform}
     * for why this is applied unconditionally rather than checked against what's actually there
     * afterward.
     */
    public void applyPositionTransform(UnaryOperator<BlockPos> transform) {
        if (boundReaderPos != null) {
            boundReaderPos = transform.apply(boundReaderPos);
            this.setChanged();
        }
        if (boundSensorPos != null) {
            boundSensorPos = transform.apply(boundSensorPos);
            this.setChanged();
        }
    }

    /**
     * Server-side only: called from each advanced sensor block's {@code setPlacedBy} to complete
     * whichever binding (if any) {@code stack} was set up for before placement - reader or
     * sensor - and announce it. A reader binding is always one-directional (the reader has no
     * notion of which sensors are bound to it). A sensor binding is one-directional too when the
     * target is a plain sensor, but mutual when the target is itself an advanced one that isn't
     * already bound to something: both then point at each other, so each drives the other's
     * signal. If the target advanced sensor is already bound to something else, this doesn't
     * touch it at all - {@link MotionSensorBlock#tryBindItemInteraction} already refuses that
     * combination at the tuning step, so reaching placement with a stale target (someone else
     * bound it in the meantime) is the only way to get here; silently falling back to "place
     * unbound" is the safe direction to be wrong in, rather than stealing the target's existing
     * slot.
     */
    static void applyPlacedBinding(Level level, BlockPos pos, @Nullable LivingEntity placer, AdvancedSensorBlockEntity sensor, ItemStack stack) {
        BlockPos readerPos = BoundSensorBlockItem.boundReader(stack);
        BlockPos sensorPos = BoundSensorBlockItem.boundSensor(stack);
        boolean bound;
        if (readerPos != null) {
            sensor.setBoundReader(readerPos);
            bound = true;
        } else if (sensorPos != null && level.getBlockEntity(sensorPos) instanceof MotionSensorBlockEntity target
                && !(target instanceof AdvancedSensorBlockEntity advanced && (advanced.getBoundReader() != null || advanced.getBoundSensor() != null))) {
            sensor.setBoundSensor(sensorPos);
            if (target instanceof AdvancedSensorBlockEntity advancedTarget) {
                advancedTarget.setBoundSensor(pos);
            }
            bound = true;
        } else {
            bound = false;
        }
        if (bound && placer instanceof Player player) {
            // green, unlike the white "tuned" message shown when the item was bound
            // (see CardReaderBlock#useItemOn/MotionSensorBlock#tryBindItemInteraction) - that
            // step only tuned the held item, this is the point the connection actually exists
            player.displayClientMessage(
                    Component.translatable("dynamickeycards.link_device.linked").withStyle(ChatFormatting.GREEN), true);
            DKSounds.confirm(level, pos);
        }
    }

    @Override
    public boolean isLinkModeEditable() {
        return boundReaderPos == null && boundSensorPos == null;
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
        if (be.boundReaderPos != null) {
            tickBoundToReader(level, pos, state, be, be.boundReaderPos);
        } else if (be.boundSensorPos != null) {
            tickBoundToSensor(level, pos, state, be, be.boundSensorPos);
        } else {
            // standalone: no different from a plain motion sensor
            MotionSensorBlockEntity.tick(level, pos, state, be);
        }
    }

    private static void tickBoundToReader(Level level, BlockPos pos, BlockState state, AdvancedSensorBlockEntity be, BlockPos readerPos) {
        long now = level.getGameTime();
        CardReaderBlockEntity reader = level.getBlockEntity(readerPos) instanceof CardReaderBlockEntity r ? r : null;
        // register mode is the owner mid-administration on the reader this sensor is bound to -
        // while bound to a reader, this sensor's whole purpose is detecting cards *for* it, so the
        // bound unit pauses together rather than just the push into the reader: no detection, no
        // local light either (own external hold, if anything else is separately driving *this*
        // sensor, still applies - see MotionSensorBlockEntity#holdExternalSignal). Cutting the
        // instant register mode starts, rather than riding out an already-running hold window,
        // matches how armRegisterMode cuts an in-progress accept pulse short instead of waiting it
        // out - see CardReaderBlock#armRegisterMode.
        if (reader != null && reader.isRegisterMode()) {
            applyPresent(level, pos, state, be, be.isExternallyHeld(now));
            return;
        }
        boolean detected = reader != null && !level.getEntitiesOfClass(Player.class, be.detectionZone(pos),
                player -> !player.isSpectator() && carriesAcceptedCard(player, reader)).isEmpty();
        if (detected) {
            be.lastCardDetectedGameTime = now;
        }
        int hold = be.getSignalLength();
        boolean shouldTrigger = detected || (hold > 0 && be.lastCardDetectedGameTime >= 0
                && now - be.lastCardDetectedGameTime < hold);

        // the sensor's own local redstone output fires right alongside the reader's - being
        // bound doesn't stop it from being a sensor in its own right at its own position. Also
        // honors its own external hold, in case something else is separately driving *this*
        // sensor (see MotionSensorBlockEntity#holdExternalSignal).
        applyPresent(level, pos, state, be, shouldTrigger || be.isExternallyHeld(now));

        if (reader == null || !shouldTrigger) {
            return;
        }
        BlockState readerState = level.getBlockState(readerPos);
        if (readerState.getValue(CardReaderBlock.MODE) != CardReaderMode.ACCEPTED
                && readerState.getBlock() instanceof CardReaderBlock readerBlock) {
            // rising edge: trigger the reader exactly like a physical tap, marking the pulse as
            // externally-originated so CardReaderBlock#tickPulseTimeout knows to release it the
            // instant we stop holding it rather than waiting out the reader's own configured
            // length - see that method's own doc.
            readerBlock.acceptPulse(readerState, level, readerPos, null, true);
        }
        // keep it held open every tick this stays true - CardReaderBlock#tickPulseTimeout is the
        // only thing that ever releases it, see that method's own doc for why
        reader.holdExternalSignal(now + HOLD_BUFFER_TICKS);
    }

    private static void tickBoundToSensor(Level level, BlockPos pos, BlockState state, AdvancedSensorBlockEntity be, BlockPos targetPos) {
        long now = level.getGameTime();
        boolean shouldTrigger = MotionSensorBlockEntity.computeShouldSignal(level, pos, state, be, now);

        // own local output as always, also honoring its own external hold in case this sensor is
        // itself simultaneously the target of another binding (the mutual advanced-advanced case)
        applyPresent(level, pos, state, be, shouldTrigger || be.isExternallyHeld(now));

        if (shouldTrigger && level.getBlockEntity(targetPos) instanceof MotionSensorBlockEntity target) {
            target.holdExternalSignal(now + HOLD_BUFFER_TICKS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (boundReaderPos != null) {
            tag.put("BoundReader", NbtUtils.writeBlockPos(boundReaderPos));
        }
        if (boundSensorPos != null) {
            tag.put("BoundSensor", NbtUtils.writeBlockPos(boundSensorPos));
        }
        if (accentColor != null) {
            tag.putString("AccentColor", accentColor.getSerializedName());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boundReaderPos = tag.contains("BoundReader") ? NbtUtils.readBlockPos(tag, "BoundReader").orElse(null) : null;
        boundSensorPos = tag.contains("BoundSensor") ? NbtUtils.readBlockPos(tag, "BoundSensor").orElse(null) : null;
        accentColor = tag.contains("AccentColor") ? DyeColor.byName(tag.getString("AccentColor"), null) : null;
    }
}
