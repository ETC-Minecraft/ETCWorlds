package com.etcmc.etcworlds.generator;

import org.bukkit.Material;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Genera un mundo completamente vacío. Coloca una plataforma 16x16 en (0,64,0) por defecto.
 * Sub-id soportado: "no-platform" para no generar plataforma.
 */
public class VoidGenerator extends ChunkGenerator {

    private final boolean platform;

    public VoidGenerator(String id) {
        this.platform = id == null || !id.equalsIgnoreCase("no-platform");
    }

    @Override
    public void generateSurface(@NotNull WorldInfo wi, @NotNull Random rand,
                                int chunkX, int chunkZ, @NotNull ChunkData chunk) {
        if (!platform) return;
        if (chunkX != 0 || chunkZ != 0) return;
        int y = 64;
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
                chunk.setBlock(x, y, z, Material.STONE);
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return true; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }

    @Override
    public @NotNull List<BlockPopulator> getDefaultPopulators(@NotNull org.bukkit.World world) {
        return Collections.emptyList();
    }
}
