package com.etcmc.etcworlds.generator;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Coloca un único bloque de césped en (0, 64, 0). El resto del comportamiento
 * (regenerar al romper, fases) lo gestiona un Listener separado en otra fase.
 */
public class OneblockGenerator extends ChunkGenerator {

    @Override
    public void generateSurface(@NotNull WorldInfo wi, @NotNull Random rand,
                                int cx, int cz, @NotNull ChunkData chunk) {
        if (cx == 0 && cz == 0) chunk.setBlock(0, 64, 0, Material.GRASS_BLOCK);
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return true; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return true; }
    @Override public boolean shouldGenerateStructures() { return false; }
}
