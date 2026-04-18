package com.etcmc.etcworlds.generator;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Islas flotantes: ruido perlin simple para colocar pequeñas islas a alturas variables.
 * Genera islotes pseudo-aleatorios reproducibles a partir de la seed del mundo.
 */
public class FloatingIslandsGenerator extends ChunkGenerator {

    @Override
    public void generateSurface(@NotNull WorldInfo wi, @NotNull Random rand,
                                int cx, int cz, @NotNull ChunkData chunk) {
        long seed = wi.getSeed();
        // Usamos un PRNG determinista por chunk
        Random r = new Random(seed ^ ((long) cx * 341873128712L + (long) cz * 132897987541L));
        int islands = r.nextInt(2); // 0–1 isla por chunk
        for (int i = 0; i < islands; i++) {
            int x = r.nextInt(16);
            int z = r.nextInt(16);
            int y = 60 + r.nextInt(80);
            int radius = 2 + r.nextInt(3);
            generateIsland(chunk, x, y, z, radius);
        }
    }

    private void generateIsland(ChunkData chunk, int cx, int cy, int cz, int radius) {
        int min = chunk.getMinHeight();
        int max = chunk.getMaxHeight() - 1;
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++)
                for (int dy = -radius; dy <= 1; dy++) {
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    if (x < 0 || x > 15 || z < 0 || z > 15) continue;
                    if (y < min || y > max) continue;
                    double dist = Math.sqrt(dx * dx + dy * dy * 1.5 + dz * dz);
                    if (dist > radius) continue;
                    Material m = (dy == 1) ? Material.GRASS_BLOCK
                            : (dy >= -1 ? Material.DIRT : Material.STONE);
                    chunk.setBlock(x, y, z, m);
                }
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return true; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return true; }
    @Override public boolean shouldGenerateMobs() { return true; }
    @Override public boolean shouldGenerateStructures() { return false; }
}
