package com.etcmc.etcworlds.model;

/**
 * Catálogo de templates incluidos. Cada template configura un generator + entorno + reglas iniciales.
 */
public enum WorldTemplate {
    NORMAL,            // generador vanilla (igual que /create)
    FLAT,              // superflat vanilla
    AMPLIFIED,         // amplified vanilla (ruido extremo)
    LARGE_BIOMES,      // biomas grandes vanilla
    VOID,              // mundo vacío con plataforma central
    SKYBLOCK,          // isla de inicio skyblock
    ONEBLOCK,          // un solo bloque que regenera
    LAYERED_VOID,      // void con varias capas a alturas configurables
    FLOATING_ISLANDS,  // islas flotantes amplified-extreme
    SINGLE_BIOME,      // todo el mundo es un único bioma
    CUSTOM_HEIGHT;     // mundo con altura/dimensión custom via datapack

    public static WorldTemplate fromString(String s) {
        if (s == null) return NORMAL;
        try { return WorldTemplate.valueOf(s.trim().toUpperCase()); }
        catch (Exception e) { return NORMAL; }
    }
}
