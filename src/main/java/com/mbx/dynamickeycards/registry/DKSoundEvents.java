package com.mbx.dynamickeycards.registry;

import com.mbx.dynamickeycards.DynamicKeycards;
import com.mbx.dynamickeycards.block.SirenTone;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The siren's two tones. They are our own sound events but not our own audio: {@code sounds.json}
 * points each at a vanilla note block sample, so this adds a definition and no file, and the mod
 * still ships no sound of its own (see {@code DKSounds}).
 *
 * <p>They exist for one reason - {@link SoundEvent#createFixedRangeEvent} pins how far a sound
 * carries, while an ordinary sound derives it from the volume ({@code 16 * volume}). A siren's
 * tone is built by retriggering one sample every tick so the copies overlap into a held note,
 * which means several are always sounding at once; asking for 32 blocks of reach through the
 * volume would make that stack deafening up close. Fixing the range lets the volume be a volume.
 */
public class DKSoundEvents {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, DynamicKeycards.MOD_ID);

    /** The car park buzzer - the quieter tier. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SIREN_CAUTION =
            register("siren_caution", SirenTone.CAUTION_RANGE);
    /** The full alarm - twice the reach. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SIREN_EMERGENCY =
            register("siren_emergency", SirenTone.EMERGENCY_RANGE);

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name, float range) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(DynamicKeycards.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createFixedRangeEvent(id, range));
    }
}
