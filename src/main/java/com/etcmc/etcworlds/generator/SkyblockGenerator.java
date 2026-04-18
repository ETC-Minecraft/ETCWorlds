package com.etcmc.etcworlds.generator;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Isla skyblock clásica en (0, 64, 0): tronco de roble + bloques de tierra,
 * cofre con starter kit (lo añade un BlockPopulator opcional fuera de aquí).
 * El resto del mundo es void.
 */
public class SkyblockGenerator extends ChunkGenerator {

    @Override
    public void generateSurface(@NotNull WorldInfo wi, @NotNull Random rand,
                                int cx, int cz, @NotNull ChunkData chunk) {
        if (cx != 0 || cz != 0) return;
        // Isla 4x4 base + 3x3 capa superior + tronco + hojas
        int y = 64;
        for (int x = 0; x < 4; x++)
            for (int z = 0; z < 4; z++) {
                chunk.setBlock(x, y, z, Material.GRASS_BLOCK);
                chunk.setBlock(x, y - 1, z, Material.DIRT);
                chunk.setBlock(x, y - 2, z, Material.DIRT);
                chunk.setBlock(x, y - 3, z, Material.STONE);
            }
        // Árbol simple
        for (int dy = 1; dy <= 4; dy++) chunk.setBlock(2, y + dy, 2, Material.OAK_LOG);
        for (int x = 1; x <= 3; x++)
            for (int z = 1; z <= 3; z++)
                for (int dy = 3; dy <= 5; dy++)
                    if (chunk.getType(x, y + dy, z).isAir())
                        chunk.setBlock(x, y + dy, z, Material.OAK_LEAVES);
        // Cofre starter
        chunk.setBlock(0, y + 1, 0, Material.CHEST);
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return true; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return true; }
    @Override public boolean shouldGenerateStructures() { return false; }
}
