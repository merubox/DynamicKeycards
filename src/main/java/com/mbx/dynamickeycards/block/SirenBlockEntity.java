package com.mbx.dynamickeycards.block;

import com.mbx.dynamickeycards.registry.DKBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A rotating warning light. Spins while either input is live - the physical redstone signal
 * reaching it from any side, or the cached value of a {@link SignalSource} it was bound to before
 * placement - and keeps spinning for {@link #getReleaseDelayTicks} more ticks after both drop, the
 * same "release delay" concept {@code ReceiverBlockEntity} already uses. Either input alone is
 * enough, the same either-one-triggers rule {@link TransmitterMode#MIXED} names.
 *
 * <p>Unlike a receiver, this one <em>polls</em> {@link DeviceIndex} on its own tick rather than
 * registering for the index's immediate push. That push exists so a receiver's redstone output
 * changes on the very tick its source does, which matters for wiring; a light that spins for
 * seconds at a time can't show one tick of latency, and staying out of the index keeps
 * {@code DeviceIndex} typed to receivers alone. It has no device id of its own for the same
 * reason: nothing ever binds <em>to</em> a siren, and it publishes nothing, so it never needs an
 * identity in the index.
 */
public class SirenBlockEntity extends BlockEntity implements WrenchPickupTarget {

    /** One full turn is 8 animation frames. */
    public static final int FRAMES_PER_TURN = 8;
    /**
     * Fastest turn: 0.2s, and the default. Also the step, so every period is a multiple of this -
     * see {@link #setRotationPeriodTicks}. One frame is then 25ms, half a tick, which is why the
     * animation is clocked in half-ticks rather than ticks (see {@link #animationFrame}).
     */
    public static final int MIN_ROTATION_TICKS = 4;
    /**
     * Slowest turn: 3s. The picker's tick row tops out at 60, and a turn speed reads naturally in
     * ticks, so that ceiling is this one too - fifteen steps of {@link #MIN_ROTATION_TICKS}.
     */
    public static final int MAX_ROTATION_TICKS = 60;

    /**
     * Where a freshly placed siren sits. Frame 0 has the reflector on the model's {@code -X} side,
     * and a siren is placed facing the way the player is looking, which puts the reflector on
     * their left; three quarter turns swing it round to the far side, so the lamp faces whoever
     * just set it down. Even, so it rests on the axis-aligned shape rather than a diagonal.
     */
    private static final int REST_FRAME = 6;

    @Nullable
    private UUID boundSourceId;
    private SirenMode mode = SirenMode.MUTE;
    private int rotationPeriodTicks = MIN_ROTATION_TICKS;
    private int releaseDelayTicks;

    /** Whether the light is turning right now - mirrored into the block's LIT state and synced for the renderer. */
    private boolean spinning;
    /**
     * Game time the current spin began, so the animation always starts at frame 0 when the light
     * comes on instead of picking up wherever a free-running world clock happened to be. Synced,
     * because the renderer derives the frame from it - see {@link #animationFrame}.
     */
    private long spinStartGameTime = -1;
    /** Game time {@link #tick} last saw either input live - the release-delay window's start. */
    private long lastActiveGameTime = -1;
    /**
     * Where the sweep is, measured from wherever it last started. A stopped siren holds its last
     * position rather than snapping back to frame 0, and the next spin picks up from that same
     * position rather than jumping back to it - a beacon that coasted to a halt facing north
     * should still be facing north with its light out, and should start turning from north.
     *
     * <p>At {@link #MIN_ROTATION_TICKS} a frame lasts half a tick, and this is only ever sampled on
     * a whole tick, so the fastest setting can only ever stop on an even frame. That is where the
     * light genuinely is at that instant - a 20Hz sample of five turns a second sees four of the
     * eight poses - not a rounding error to correct.
     */
    private int frameOffset = REST_FRAME;
    private final WrenchPickupState wrenchPickup = new WrenchPickupState();

    public SirenBlockEntity(BlockPos pos, BlockState state) {
        super(DKBlockEntities.SIREN.get(), pos, state);
    }

    @Nullable
    public UUID getBoundSource() {
        return boundSourceId;
    }

    public void setBoundSource(@Nullable UUID sourceId) {
        this.boundSourceId = sourceId;
        this.syncToClient();
    }

    public SirenMode getMode() {
        return mode;
    }

    public void setMode(SirenMode mode) {
        this.mode = mode;
        this.syncToClient();
    }

    /** How long one full turn takes, in ticks - 4 (0.2s) to 240 (12s), always a multiple of 4. */
    public int getRotationPeriodTicks() {
        return rotationPeriodTicks;
    }

    public void setRotationPeriodTicks(int ticks) {
        this.rotationPeriodTicks = snapRotationPeriod(ticks);
        this.syncToClient();
    }

    /**
     * Snaps to whole 0.2s steps within range: the UI only offers those, but NBT from an older or
     * hand-edited save could hold anything, and a period that isn't a multiple of
     * {@link #MIN_ROTATION_TICKS} would give frames of uneven length.
     *
     * <p>Its own method because loading has to snap too, and going through the setter to do it
     * would have a save being read announce itself to every client watching.
     */
    private static int snapRotationPeriod(int ticks) {
        int clamped = Mth.clamp(ticks, MIN_ROTATION_TICKS, MAX_ROTATION_TICKS);
        return Math.round(clamped / (float) MIN_ROTATION_TICKS) * MIN_ROTATION_TICKS;
    }

    /** Restores the turn speed to its default (fastest) - half of what the reset button does. */
    public void resetRotationPeriodTicks() {
        setRotationPeriodTicks(MIN_ROTATION_TICKS);
    }

    /**
     * How long the light keeps turning after both inputs drop. {@code 0} = stops the instant they
     * do, the default - this only extends the tail, it never shortens a spin that its input is
     * still holding up.
     */
    public int getReleaseDelayTicks() {
        return releaseDelayTicks;
    }

    public void setReleaseDelayTicks(int ticks) {
        this.releaseDelayTicks = Math.max(0, ticks);
        this.syncToClient();
    }

    /** Restores the release delay to its default (0) - the other half of the reset button. */
    public void resetReleaseDelayTicks() {
        setReleaseDelayTicks(0);
    }

    public boolean isSpinning() {
        return spinning;
    }

    /**
     * Which of the {@value #FRAMES_PER_TURN} frames to draw right now. Derived from the world clock
     * rather than counted up per tick, so it costs nothing to keep and every client that can see
     * this block agrees on it without any of them being told. A stopped siren holds
     * {@link #frameOffset}, and a spinning one counts up from it.
     *
     * <p>Measured in half-ticks, not ticks: at the fastest speed a frame lasts 25ms and a tick is
     * 50ms, so a whole-tick clock would show every other frame and the sweep would look like it
     * jumped 90 degrees at a time. {@code partialTick} supplies the half. Every period is a
     * multiple of {@link #MIN_ROTATION_TICKS} (4 ticks = 8 half-ticks), so a frame is always a
     * whole number of half-ticks and none is ever shown longer than its neighbours.
     */
    public int animationFrame(long gameTime, float partialTick) {
        if (!spinning || spinStartGameTime < 0) {
            return frameOffset;
        }
        int halfTicksPerFrame = rotationPeriodTicks * 2 / FRAMES_PER_TURN;
        double elapsedHalfTicks = (gameTime - spinStartGameTime + partialTick) * 2.0;
        long framesTurned = (long) (elapsedHalfTicks / halfTicksPerFrame);
        // counted in whole frames on top of the offset rather than by backdating the start time:
        // a frame can be half a tick long, which a long start time cannot express
        return (int) Math.floorMod(framesTurned + frameOffset, FRAMES_PER_TURN);
    }

    /**
     * Turns this tick's two inputs into {@link #spinning}. Either the physical redstone reaching
     * this block or the bound source's cached value is enough; once both are gone, the light
     * keeps turning until the release delay lapses.
     *
     * @return whether {@link #spinning} changed this tick.
     */
    static boolean tick(Level level, BlockPos pos, SirenBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        boolean live = level.getBestNeighborSignal(pos) > 0
                || (be.boundSourceId != null && DeviceIndex.get(serverLevel).getSignal(be.boundSourceId) > 0);
        long now = level.getGameTime();
        boolean changed = be.updateSpin(live, now);
        if (be.spinning && be.mode.audible()) {
            // every tick, not only on a change - the tone is retriggered continuously, see SirenTone#play
            SirenTone.play(serverLevel, pos, be.mode, now - be.spinStartGameTime);
        }
        return changed;
    }

    /** @return whether {@link #spinning} changed, so {@link #tick} knows whether to update the block. */
    private boolean updateSpin(boolean live, long now) {
        if (live) {
            lastActiveGameTime = now;
            return setSpinning(true, now);
        }
        if (releaseDelayTicks > 0 && lastActiveGameTime >= 0
                && now - lastActiveGameTime < releaseDelayTicks) {
            return false;
        }
        return setSpinning(false, now);
    }

    /** @return whether the state actually changed, so the caller knows whether to update the block. */
    private boolean setSpinning(boolean value, long now) {
        if (value == spinning) {
            return false;
        }
        if (!value) {
            // freeze where the sweep actually got to, before spinning goes false
            frameOffset = animationFrame(now, 0f);
        }
        spinning = value;
        if (value) {
            // the offset is left alone, so the sweep resumes from the pose it stopped in
            spinStartGameTime = now;
        }
        syncToClient();
        return true;
    }

    @Override
    public boolean isWrenchPickupPending() {
        return wrenchPickup.isPending(level);
    }

    @Override
    public void armWrenchPickupPending() {
        wrenchPickup.arm(level);
    }

    @Override
    public void clearPendingActions() {
        wrenchPickup.clear();
    }

    private void syncToClient() {
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (boundSourceId != null) {
            tag.putUUID("BoundSource", boundSourceId);
        }
        tag.putString("Mode", mode.name());
        tag.putInt("RotationPeriod", rotationPeriodTicks);
        tag.putInt("ReleaseDelay", releaseDelayTicks);
        tag.putBoolean("Spinning", spinning);
        tag.putLong("SpinStart", spinStartGameTime);
        tag.putInt("Frame", frameOffset);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boundSourceId = tag.hasUUID("BoundSource") ? tag.getUUID("BoundSource") : null;
        mode = SirenMode.byName(tag.getString("Mode"));
        rotationPeriodTicks = snapRotationPeriod(
                tag.contains("RotationPeriod") ? tag.getInt("RotationPeriod") : MIN_ROTATION_TICKS);
        releaseDelayTicks = Math.max(0, tag.getInt("ReleaseDelay"));
        spinning = tag.getBoolean("Spinning");
        spinStartGameTime = tag.contains("SpinStart") ? tag.getLong("SpinStart") : -1;
        frameOffset = Math.floorMod(
                tag.contains("Frame") ? tag.getInt("Frame") : REST_FRAME, FRAMES_PER_TURN);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
