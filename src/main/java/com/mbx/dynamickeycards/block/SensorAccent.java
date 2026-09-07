package com.mbx.dynamickeycards.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;

/**
 * An advanced sensor's cosmetic accent, as a blockstate value: the native undyed look plus one
 * constant per dye.
 *
 * <p>The accent is stored on {@link AdvancedSensorBlockEntity} (it has to be - a block entity is
 * what survives being picked up and re-placed), but it also has to exist as a blockstate property
 * so the blockstate file can pick the model. Rendering a block entity by hand instead skips the
 * vanilla path that shades each face by its direction, which left every dyed sensor uniformly lit
 * and its faces indistinguishable.
 */
public enum SensorAccent implements StringRepresentable {

    NONE("none"),
    WHITE("white"),
    ORANGE("orange"),
    MAGENTA("magenta"),
    LIGHT_BLUE("light_blue"),
    YELLOW("yellow"),
    LIME("lime"),
    PINK("pink"),
    GRAY("gray"),
    LIGHT_GRAY("light_gray"),
    CYAN("cyan"),
    PURPLE("purple"),
    BLUE("blue"),
    BROWN("brown"),
    GREEN("green"),
    RED("red"),
    BLACK("black");

    private final String name;

    SensorAccent(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** {@code null} - the undyed look - maps to {@link #NONE}. */
    public static SensorAccent of(@Nullable DyeColor color) {
        return color == null ? NONE : values()[color.getId() + 1];
    }

    /** {@code null} for {@link #NONE}, mirroring {@link AdvancedSensorBlockEntity#getAccentColor}. */
    @Nullable
    public DyeColor toDye() {
        return this == NONE ? null : DyeColor.byId(ordinal() - 1);
    }
}
