package com.etcmc.etcworlds.generator;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Fuerza a TODO el mundo a generar un único bioma. Se combina con el generator
 * vanilla (no se sobrescribe el terreno) para tener "todo desierto", "todo jungle", etc.
 */
public class SingleBiomeProvider extends BiomeProvider {

    private final Biome biome;

    public SingleBiomeProvider(String biomeName) {
        Biome b;
        try {
            b = Biome.valueOf(biomeName == null ? "PLAINS" : biomeName.trim().toUpperCase()
                    .replace("MINECRAFT:", ""));
        } catch (Exception e) {
            b = Biome.PLAINS;
        }
        this.biome = b;
    }

    @Override
    public @NotNull Biome getBiome(@NotNull WorldInfo wi, int x, int y, int z) {
        return biome;
    }

    @Override
    public @NotNull List<Biome> getBiomes(@NotNull WorldInfo wi) {
        return Collections.singletonList(biome);
    }
}
