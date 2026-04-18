package com.etcmc.etcworlds.generator;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Genera capas planas a alturas configurables. Sub-id formato: "y1:material;y2:material;..."
 * Ejemplo: "32:bedrock;64:stone;100:glass"
 */
public class LayeredVoidGenerator extends ChunkGenerator {

    private final int[][] layers; // [layer][0]=y, [1]=materialOrdinal not used; we use cached materials parallel

    private final java.util.List<int[]> ys = new java.util.ArrayList<>();
    private final java.util.List<Material> mats = new java.util.ArrayList<>();

    public LayeredVoidGenerator(String id) {
        if (id != null && !id.isBlank()) {
            for (String token : id.split(";")) {
                String[] parts = token.split(":");
                if (parts.length != 2) continue;
                try {
                    int y = Integer.parseInt(parts[0].trim());
                    Material m = Material.matchMaterial(parts[1].trim().toUpperCase());
                    if (m == null || !m.isBlock()) continue;
                    ys.add(new int[]{y});
                    mats.add(m);
                } catch (NumberFormatException ignored) {}
            }
        }
        if (ys.isEmpty()) {
            ys.add(new int[]{64});
            mats.add(Material.STONE);
        }
        this.layers = ys.toArray(new int[0][]);
    }

    @Override
    public void generateSurface(@NotNull WorldInfo wi, @NotNull Random rand,
                                int cx, int cz, @NotNull ChunkData chunk) {
        for (int i = 0; i < layers.length; i++) {
            int y = layers[i][0];
            Material m = mats.get(i);
            if (y < chunk.getMinHeight() || y >= chunk.getMaxHeight()) continue;
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++)
                    chunk.setBlock(x, y, z, m);
        }
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return true; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
}
