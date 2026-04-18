package com.etcmc.etcworlds.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lista curada de seeds famosos / interesantes para sugerir al crear un mundo.
 * Solo aplica a templates que respetan seed (NORMAL, FLAT, AMPLIFIED, LARGE_BIOMES).
 */
public final class SeedPresets {
    private SeedPresets() {}

    public static final Map<String, Long> PRESETS = new LinkedHashMap<>();
    static {
        PRESETS.put("classic_island",      8678942899319966093L);
        PRESETS.put("aldea_inicio",        -132520481L);
        PRESETS.put("mansion_cerca",       -7868018157604429584L);
        PRESETS.put("frozen_kingdom",      -7901864547107776100L);
        PRESETS.put("cherry_grove",        4928947395028471234L);
        PRESETS.put("mushroom_island",     -3470830629921518L);
        PRESETS.put("desert_temple",       -8865607334649884008L);
        PRESETS.put("speedrun_friendly",   1L);
    }

    public static Long get(String name) { return PRESETS.get(name.toLowerCase()); }
}
