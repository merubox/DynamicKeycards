package com.mbx.dynamickeycards;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * Feedback tones for the card machines, so the result is audible without watching the
 * status lights: high bell = pass/complete, mid pling = registered, low pling = removed
 * or cancelled, low bass = denied. Vanilla note block sounds, plus two sounds that stand
 * outside that vocabulary on purpose ({@link #manualTrigger}, {@link #valueConfirm}) — no
 * custom assets. The siren is not here: a tone held for as long as a light turns is not feedback
 * and has none of this in common with it, so it lives in {@link com.mbx.dynamickeycards.block.SirenTone}.
 * Call server-side only; the sound is broadcast to nearby players.
 */
public final class DKSounds {

    private DKSounds() {
    }

    /** Card accepted / duplication complete. */
    public static void accept(Level level, BlockPos pos) {
        play(level, pos, SoundEvents.NOTE_BLOCK_BELL.value(), 0.5f, 1.6f);
    }

    /** Registration written. */
    public static void confirm(Level level, BlockPos pos) {
        play(level, pos, SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, 1.4f);
    }

    /** Registration removed, register mode cancelled, reset, copy cancelled. */
    public static void remove(Level level, BlockPos pos) {
        play(level, pos, SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, 0.8f);
    }

    /** Card or action rejected. */
    public static void deny(Level level, BlockPos pos) {
        play(level, pos, SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.6f);
    }

    /**
     * A transmitter's manual trigger in mixed mode - vanilla's own stone button press, at vanilla's
     * own volume. The transmitter is standing in for a button wired into it (its default trigger
     * length is literally the stone button's), so it should sound like pressing one.
     */
    public static void manualTrigger(Level level, BlockPos pos) {
        play(level, pos, SoundEvents.STONE_BUTTON_CLICK_ON, 1f, 1f);
    }

    /** Register mode armed / duplication source inserted. */
    public static void arm(Level level, BlockPos pos) {
        play(level, pos, SoundEvents.NOTE_BLOCK_PLING.value(), 0.35f, 1.1f);
    }

    /**
     * A value confirmed on a device's config UI (signal length, trigger duration) - two vanilla
     * sounds layered quietly on top of each other, a sharp high click plus a very faint xylophone
     * note. Outside the five-tone vocabulary above on purpose: it exists to match what players
     * already hear from value-adjustment scales elsewhere, not to say anything about a card.
     */
    public static void valueConfirm(Level level, BlockPos pos) {
        play(level, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, 0.25f, 2f);
        play(level, pos, SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), 0.03f, 1.125f);
    }

    private static void play(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }
}
