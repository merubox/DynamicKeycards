package com.mbx.dynamickeycards;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * Feedback tones for the card machines, so the result is audible without watching the
 * status lights: high bell = pass/complete, mid pling = registered, low pling = removed
 * or cancelled, low bass = denied. All vanilla note block sounds — no custom assets.
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

    /** Register mode armed / duplication source inserted. */
    public static void arm(Level level, BlockPos pos) {
        play(level, pos, SoundEvents.NOTE_BLOCK_PLING.value(), 0.35f, 1.1f);
    }

    private static void play(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }
}
