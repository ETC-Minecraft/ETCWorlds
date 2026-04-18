package com.etcmc.etcworlds.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Genera datapacks "on the fly" para crear dimension_type custom con altura personalizada.
 * Esto permite que un mundo tenga p.e. min_y=-256, height=768, logical_height=768.
 *
 * El datapack se escribe en: world/<worldName>/datapacks/etcworlds_<worldName>/
 * Bukkit lo carga automáticamente al inicializar el mundo. La dimensión usable
 * tiene id "etcworlds:custom_<worldName>".
 *
 * NOTA: La activación del dimension_type custom en un mundo Bukkit requiere
 * que el mundo se cree apuntando a un dimension_type. Bukkit no expone esto
 * directamente, así que esta clase escribe los archivos del datapack y
 * documenta cómo asociarlo. El generator + height ratio son el approach
 * compatible disponible sin NMS.
 */
public final class DatapackHeightGenerator {

    private DatapackHeightGenerator() {}

    /**
     * Escribe un datapack con un dimension_type custom para el mundo dado.
     * @param worldFolder carpeta raíz del mundo (donde vive level.dat).
     * @param worldName   nombre del mundo (sin barras).
     * @param minY        Y mínimo (múltiplo de 16, entre -2048 y 2031).
     * @param height      altura total (múltiplo de 16, máx 4064).
     * @return el ID del dimension_type creado: "etcworlds:custom_<worldName>".
     */
    public static String write(File worldFolder, String worldName, int minY, int height) throws IOException {
        // Validaciones / clamping
        minY = clamp(minY, -2048, 2031);
        height = Math.max(16, Math.min(height, 4064));
        if (minY % 16 != 0) minY = (minY / 16) * 16;
        if (height % 16 != 0) height = ((height + 15) / 16) * 16;
        int logical = Math.min(height, 4064);

        String safeName = worldName.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        File dpRoot = new File(worldFolder, "datapacks/etcworlds_" + safeName);
        File dataDir = new File(dpRoot, "data/etcworlds/dimension_type");
        if (!dataDir.exists() && !dataDir.mkdirs())
            throw new IOException("No se pudo crear datapack dir: " + dataDir);

        // pack.mcmeta
        File mcmeta = new File(dpRoot, "pack.mcmeta");
        try (FileWriter w = new FileWriter(mcmeta)) {
            w.write("{\n  \"pack\": {\n    \"pack_format\": 41,\n    \"description\": \"ETCWorlds custom height for " + worldName + "\"\n  }\n}\n");
        }

        // dimension_type/custom_<world>.json (basado en overworld vanilla, ajustando height/min_y)
        File dt = new File(dataDir, "custom_" + safeName + ".json");
        try (FileWriter w = new FileWriter(dt)) {
            w.write("{\n");
            w.write("  \"ultrawarm\": false,\n");
            w.write("  \"natural\": true,\n");
            w.write("  \"piglin_safe\": false,\n");
            w.write("  \"respawn_anchor_works\": false,\n");
            w.write("  \"bed_works\": true,\n");
            w.write("  \"has_raids\": true,\n");
            w.write("  \"has_skylight\": true,\n");
            w.write("  \"has_ceiling\": false,\n");
            w.write("  \"coordinate_scale\": 1.0,\n");
            w.write("  \"ambient_light\": 0.0,\n");
            w.write("  \"infiniburn\": \"#minecraft:infiniburn_overworld\",\n");
            w.write("  \"effects\": \"minecraft:overworld\",\n");
            w.write("  \"min_y\": " + minY + ",\n");
            w.write("  \"height\": " + height + ",\n");
            w.write("  \"logical_height\": " + logical + ",\n");
            w.write("  \"monster_spawn_block_light_limit\": 0,\n");
            w.write("  \"monster_spawn_light_level\": { \"type\": \"minecraft:uniform\", \"value\": { \"min_inclusive\": 0, \"max_inclusive\": 7 } }\n");
            w.write("}\n");
        }
        return "etcworlds:custom_" + safeName;
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
