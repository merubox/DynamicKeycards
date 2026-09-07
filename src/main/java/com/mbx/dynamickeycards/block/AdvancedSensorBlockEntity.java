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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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
 *   <li><b>Another advanced sensor</b> (a plain sensor is never a valid target - see
 *   {@code MotionSensorBlock#tryBindItemInteraction}'s own doc for why that role moved to the
 *   receiver instead) - it keeps its own ordinary zone detection, but also relays that same
 *   on/off pattern (release delay included) into the target's own signal remotely, via
 *   {@link MotionSensorBlockEntity#holdExternalSignal}. The binding is set up mutually on
 *   placement (see {@link #applyPlacedBinding}), so each one drives the other.</li>
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
 * interaction); a gold nugget clears it back to the native undyed look.
 */
public class AdvancedSensorBlockEntity extends MotionSensorBlockEntity implements SignalSource {

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
    private UUID boundReaderId;
    @Nullable
    private UUID boundSensorId;
    /** Only meaningful while {@link #boundReaderId} is set - see {@link BoundReaderMode}'s own doc. */
    private BoundReaderMode boundReaderMode = BoundReaderMode.SENSOR_CENTRIC_SIMULTANEOUS;
    /** Best-effort pre-0.1.8 save migration; resolved to {@link #boundReaderId} in {@link #onLoad}, then discarded. See {@code CardReaderBlockEntity#resolveLegacyLinkedReaders}. */
    @Nullable
    private BlockPos legacyBoundReaderPos;
    /** Same as {@link #legacyBoundReaderPos}, for the sensor-target case. */
    @Nullable
    private BlockPos legacyBoundSensorPos;
    /** Bound-mode equivalent of the parent's own last-detected tracking, kept separate since only one mode is ever active. */
    private long lastCardDetectedGameTime = -1;
    /**
     * Raw (no release-delay/hold applied) 0-or-15 detection this tick, whichever binding mode is
     * active - what {@link #getSignalSourceStrength} exposes to {@link DeviceIndex}. Deliberately
     * separate from this sensor's own local output (which does apply release delay/hold), per the
     * "local duration settings never leak into the wireless value" principle - see
     * {@link MotionSensorBlockEntity.DetectionResult}'s own doc.
     */
    private int lastRawSignalStrength;
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

    public void setBoundReader(@Nullable UUID readerId) {
        this.boundReaderId = readerId;
        if (readerId != null) {
            // bound to exactly one thing at a time - see the class doc
            this.boundSensorId = null;
            // becoming bound: the reader owns the real mode/frequency now, so force back to
            // NORMAL (and off any Create Link registration of its own) - harmless if already there
            super.setSignalMode(SignalMode.NORMAL);
            // fresh binding starts from the default split rather than silently carrying over
            // whatever was picked for a previous binding
            this.boundReaderMode = BoundReaderMode.SENSOR_CENTRIC_SIMULTANEOUS;
        }
        this.setChanged();
    }

    @Nullable
    public UUID getBoundReader() {
        return boundReaderId;
    }

    public BoundReaderMode getBoundReaderMode() {
        return boundReaderMode;
    }

    /** Only meaningful while bound to a reader - a no-op otherwise, since {@link #tickBoundToReader} is the only thing that ever reads it. */
    public void setBoundReaderMode(BoundReaderMode mode) {
        this.boundReaderMode = mode;
        this.syncToClient();
    }

    public void setBoundSensor(@Nullable UUID sensorId) {
        this.boundSensorId = sensorId;
        if (sensorId != null) {
            // bound to exactly one thing at a time - see the class doc
            this.boundReaderId = null;
            super.setSignalMode(SignalMode.NORMAL);
        }
        this.setChanged();
    }

    @Nullable
    public UUID getBoundSensor() {
        return boundSensorId;
    }

    @Override
    public int getSignalSourceStrength() {
        return lastRawSignalStrength;
    }

    /** Publishes {@code raw} only when it actually differs from last tick's - {@link DeviceIndex#updateSignal} would no-op either way, but this also skips the {@code UUID} lookup. */
    private void publishRawSignal(Level level, boolean raw) {
        int strength = raw ? 15 : 0;
        if (strength != lastRawSignalStrength) {
            lastRawSignalStrength = strength;
            publishSignalSource(level);
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
        UUID readerId = BoundSensorBlockItem.boundReader(stack);
        UUID sensorId = BoundSensorBlockItem.boundSensor(stack);
        boolean bound = false;
        if (readerId != null) {
            sensor.setBoundReader(readerId);
            bound = true;
        } else if (sensorId != null && level instanceof ServerLevel serverLevel) {
            // a plain sensor is never a valid target (see MotionSensorBlock#tryBindItemInteraction's
            // own doc) - this only ever matters for an item tuned before that rule existed, since a
            // freshly-tuned one can no longer point at a plain sensor's id in the first place
            BlockPos targetPos = DeviceIndex.get(serverLevel).getPosition(sensorId);
            if (targetPos != null && level.getBlockEntity(targetPos) instanceof AdvancedSensorBlockEntity target
                    && target.getBoundReader() == null && target.getBoundSensor() == null) {
                sensor.setBoundSensor(sensorId);
                target.setBoundSensor(sensor.getDeviceId());
                bound = true;
            }
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

    /**
     * True even while bound to a reader - unlike being bound to a *sensor*, the three mode
     * buttons stay live there, just repurposed to {@link BoundReaderMode} instead of
     * {@link SignalMode} (see {@code LinkDeviceMenu}/{@code LinkDeviceScreen}'s own bound-reader
     * special-casing). {@link #isFrequencyEditable} is the one that stays locked in both bound
     * states.
     */
    @Override
    public boolean isLinkModeEditable() {
        return boundSensorId == null;
    }

    @Override
    public boolean isFrequencyEditable() {
        return boundReaderId == null && boundSensorId == null;
    }

    @Override
    public void setSignalMode(SignalMode mode) {
        if (isFrequencyEditable()) {
            super.setSignalMode(mode);
        }
    }

    @Override
    public void setFrequencySlot(int index, ItemStack stack) {
        if (isFrequencyEditable()) {
            super.setFrequencySlot(index, stack);
        }
    }

    private static boolean carriesAcceptedCard(Player player, CardReaderBlockEntity reader) {
        return reader.acceptsAny(player.getInventory());
    }

    static void tick(Level level, BlockPos pos, BlockState state, AdvancedSensorBlockEntity be) {
        if (be.boundReaderId != null) {
            tickBoundToReader(level, pos, state, be, be.boundReaderId);
        } else if (be.boundSensorId != null) {
            tickBoundToSensor(level, pos, state, be, be.boundSensorId);
        } else {
            tickStandalone(level, pos, state, be);
        }
    }

    /** Unbound: behaves like a plain motion sensor, but (unlike the plain one) also publishes its raw detection as a {@link SignalSource}. */
    private static void tickStandalone(Level level, BlockPos pos, BlockState state, AdvancedSensorBlockEntity be) {
        long now = level.getGameTime();
        DetectionResult detection = MotionSensorBlockEntity.computeShouldSignal(level, pos, state, be, now);
        applyPresent(level, pos, state, be, detection.withLinger() || be.isExternallyHeld(now));
        be.publishRawSignal(level, detection.raw());
    }

    private static void tickBoundToReader(Level level, BlockPos pos, BlockState state, AdvancedSensorBlockEntity be, UUID readerId) {
        long now = level.getGameTime();
        BlockPos readerPos = level instanceof ServerLevel serverLevel ? DeviceIndex.get(serverLevel).getPosition(readerId) : null;
        CardReaderBlockEntity reader = readerPos != null && level.getBlockEntity(readerPos) instanceof CardReaderBlockEntity r ? r : null;
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
            be.publishRawSignal(level, false);
            // no override push here (unlike before) - letting SENSOR_CENTRIC_SIMULTANEOUS's own
            // override simply expire on its own is enough, and correctly falls back to this
            // reader's own MODE the same way it does whenever this sensor isn't mid-detection -
            // see the override calls below for why that matters
            return;
        }
        boolean detected = reader != null && !level.getEntitiesOfClass(Player.class, be.detectionZone(pos),
                player -> !player.isSpectator() && carriesAcceptedCard(player, reader)).isEmpty();
        if (detected) {
            be.lastCardDetectedGameTime = now;
        }
        be.publishRawSignal(level, detected);
        int hold = be.getSignalLength();
        boolean shouldTrigger = detected || (hold > 0 && be.lastCardDetectedGameTime >= 0
                && now - be.lastCardDetectedGameTime < hold);

        // Both override mechanisms below are deliberately gated on shouldTrigger, not called
        // unconditionally every tick this sensor is simply bound in the given mode: a reader can
        // still be tapped directly by hand at any time regardless of what's bound to it, and that
        // direct tap needs to show up on a receiver bound to this reader via the reader's own
        // MODE-based broadcast (see CardReaderBlockEntity#getSignalSourceStrength). An override
        // refreshed every tick with no regard for shouldTrigger would never actually expire while
        // this sensor stays bound, permanently shadowing the reader's own MODE and silencing every
        // direct tap - exactly the bug this gating avoids.
        if (reader != null && shouldTrigger && be.boundReaderMode == BoundReaderMode.SENSOR_CENTRIC_SIMULTANEOUS) {
            // a receiver bound to the reader should be indistinguishable from one bound straight
            // to this sensor - see CardReaderBlockEntity#publishSensorRawSignal
            reader.publishSensorRawSignal(level, detected);
        }

        // READER_ONLY drops the sensor's own local output entirely - the other two modes fire it
        // alongside the reader's, same as this sensor always has, still also honoring its own
        // external hold in case something else is separately driving *this* sensor (see
        // MotionSensorBlockEntity#holdExternalSignal)
        boolean ownOutputActive = be.boundReaderMode == BoundReaderMode.READER_ONLY
                ? be.isExternallyHeld(now)
                : shouldTrigger || be.isExternallyHeld(now);
        applyPresent(level, pos, state, be, ownOutputActive);

        if (reader != null && shouldTrigger && be.boundReaderMode == BoundReaderMode.SIMULTANEOUS) {
            // SIMULTANEOUS's reader manages its own release independently of this sensor's own
            // hold, so it can end up re-triggering repeatedly for as long as this sensor keeps
            // detecting - see CardReaderBlockEntity#MOMENTARY_TICKS's own doc for why its wireless
            // broadcast gets a short blip per re-trigger instead of this reader's own full
            // held-open MODE
            reader.keepSimultaneousBlipModeActive(level);
        }
        if (reader == null || !shouldTrigger) {
            return;
        }
        // SIMULTANEOUS deliberately does *not* mark the pulse externally-originated: the reader
        // then manages its own release using its own configured signal length, independently of
        // this sensor's own hold - the two named "*_SIMULTANEOUS" values only agree on both
        // sides triggering together, not on whose timing wins, see BoundReaderMode's own doc
        boolean externallyOriginated = be.boundReaderMode != BoundReaderMode.SIMULTANEOUS;
        BlockState readerState = level.getBlockState(readerPos);
        if (readerState.getValue(CardReaderBlock.MODE) != CardReaderMode.ACCEPTED
                && readerState.getBlock() instanceof CardReaderBlock readerBlock) {
            // rising edge: trigger the reader exactly like a physical tap
            readerBlock.acceptPulse(readerState, level, readerPos, null, externallyOriginated);
            if (be.boundReaderMode == BoundReaderMode.SIMULTANEOUS) {
                // overrides acceptPulse's own MODE-based publish with this re-trigger's own blip
                reader.markSignalSourceTriggered();
            }
        }
        if (externallyOriginated) {
            // keep it held open every tick this stays true - CardReaderBlock#tickPulseTimeout is
            // the only thing that ever releases it, see that method's own doc for why
            reader.holdExternalSignal(now + HOLD_BUFFER_TICKS);
        }
        // SIMULTANEOUS: no hold call - the reader was triggered as an ordinary (non-externally-
        // originated) pulse above, so it releases on its own schedule regardless of what this
        // sensor keeps detecting, and will simply re-trigger again next time MODE has cycled back
        // off if shouldTrigger is still true - an independent, possibly-repeating pulse train
    }

    private static void tickBoundToSensor(Level level, BlockPos pos, BlockState state, AdvancedSensorBlockEntity be, UUID targetId) {
        long now = level.getGameTime();
        DetectionResult detection = MotionSensorBlockEntity.computeShouldSignal(level, pos, state, be, now);
        boolean shouldTrigger = detection.withLinger();
        be.publishRawSignal(level, detection.raw());

        // own local output as always, also honoring its own external hold in case this sensor is
        // itself simultaneously the target of another binding (the mutual advanced-advanced case)
        applyPresent(level, pos, state, be, shouldTrigger || be.isExternallyHeld(now));

        BlockPos targetPos = shouldTrigger && level instanceof ServerLevel serverLevel
                ? DeviceIndex.get(serverLevel).getPosition(targetId) : null;
        if (targetPos != null && level.getBlockEntity(targetPos) instanceof MotionSensorBlockEntity target) {
            target.holdExternalSignal(now + HOLD_BUFFER_TICKS);
        }
    }

    /**
     * Resolves {@link #legacyBoundReaderPos}/{@link #legacyBoundSensorPos} (pre-0.1.8 saves) into
     * {@link #boundReaderId}/{@link #boundSensorId}, best-effort - only if the target's chunk
     * happens to already be loaded right now (no forced loading). Unresolvable ones are silently
     * dropped; either way the legacy fields are cleared after this one attempt. Mirrors
     * {@code CardReaderBlockEntity#resolveLegacyLinkedReaders}.
     */
    private void resolveLegacyBinding() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (legacyBoundReaderPos != null && serverLevel.hasChunk(legacyBoundReaderPos.getX() >> 4, legacyBoundReaderPos.getZ() >> 4)
                && serverLevel.getBlockEntity(legacyBoundReaderPos) instanceof CardReaderBlockEntity target) {
            boundReaderId = target.getDeviceId();
            this.setChanged();
        } else if (legacyBoundSensorPos != null && serverLevel.hasChunk(legacyBoundSensorPos.getX() >> 4, legacyBoundSensorPos.getZ() >> 4)
                && serverLevel.getBlockEntity(legacyBoundSensorPos) instanceof MotionSensorBlockEntity target) {
            boundSensorId = target.getDeviceId();
            this.setChanged();
        }
        legacyBoundReaderPos = null;
        legacyBoundSensorPos = null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        resolveLegacyBinding();
        syncAccentToBlockState();
    }

    /**
     * Pushes the saved accent into the blockstate for a sensor placed before the accent became a
     * blockstate property - those saves have the color in NBT only, so their blockstate would sit
     * at {@link SensorAccent#NONE} and render undyed. Server-side only, and a no-op once they
     * already agree.
     */
    private void syncAccentToBlockState() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(AdvancedSensorDyeing.ACCENT)) {
            return;
        }
        SensorAccent expected = SensorAccent.of(accentColor);
        if (state.getValue(AdvancedSensorDyeing.ACCENT) != expected) {
            level.setBlock(worldPosition, state.setValue(AdvancedSensorDyeing.ACCENT, expected), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (boundReaderId != null) {
            tag.putUUID("BoundReaderId", boundReaderId);
            tag.putString("BoundReaderMode", boundReaderMode.name());
        }
        if (boundSensorId != null) {
            tag.putUUID("BoundSensorId", boundSensorId);
        }
        if (accentColor != null) {
            tag.putString("AccentColor", accentColor.getSerializedName());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("BoundReaderId")) {
            boundReaderId = tag.getUUID("BoundReaderId");
            legacyBoundReaderPos = null;
            boundReaderMode = BoundReaderMode.byName(tag.getString("BoundReaderMode"));
        } else {
            boundReaderId = null;
            legacyBoundReaderPos = tag.contains("BoundReader") ? NbtUtils.readBlockPos(tag, "BoundReader").orElse(null) : null;
        }
        if (tag.hasUUID("BoundSensorId")) {
            boundSensorId = tag.getUUID("BoundSensorId");
            legacyBoundSensorPos = null;
        } else {
            boundSensorId = null;
            legacyBoundSensorPos = tag.contains("BoundSensor") ? NbtUtils.readBlockPos(tag, "BoundSensor").orElse(null) : null;
        }
        accentColor = tag.contains("AccentColor") ? DyeColor.byName(tag.getString("AccentColor"), null) : null;
    }
}
