package com.etcmc.etcworlds.model;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Reglas y propiedades por mundo. Se serializan en mundos/<nombre>/etcworlds.yml.
 * Mantenemos defaults sensatos para que un mundo nuevo no requiera tocar nada.
 */
public class WorldRules {

    // === Identidad / generación (read-only tras crear) ===
    public String name;
    public WorldTemplate template = WorldTemplate.NORMAL;
    public World.Environment environment = World.Environment.NORMAL;
    public World.Environment ambient = World.Environment.NORMAL; // qué mobs/portales usa
    public long seed = 0L;
    public boolean generateStructures = true;
    public String generatorId = ""; // sub-id pasado al ChunkGenerator
    public String biomeForce = "";  // ej. "minecraft:desert"
    public int customMinY = Integer.MIN_VALUE;
    public int customMaxY = Integer.MIN_VALUE;
    public String datapackName = ""; // si tiene datapack auto generado

    // === Spawn ===
    public double spawnX = 0.5, spawnY = 80, spawnZ = 0.5;
    public float spawnYaw = 0f, spawnPitch = 0f;
    public boolean keepSpawnInMemory = false;

    // === Comportamiento ===
    public GameMode gamemode = null; // null = libre, distinto = forzado
    public boolean pvp = true;
    public boolean mobSpawn = true;
    public boolean animalSpawn = true;
    public boolean keepInventoryOnDeath = false;
    public boolean fallDamage = true;
    public boolean weatherEnabled = true;
    public boolean weatherLocked = false;     // si true, fija el estado actual
    public boolean timeLocked = false;        // si true, fija el time actual
    public long fixedTimeTick = 6000;         // mediodía si time-locked
    public boolean fly = false;               // fuerza fly al entrar
    public boolean buildEnabled = true;       // permitir colocar/romper bloques
    public boolean hunger = true;             // si no, sacia automáticamente
    public boolean autoSave = true;
    public int viewDistance = -1;             // -1 = usar el global
    public int simulationDistance = -1;

    // === Worldborder ===
    public double borderCenterX = 0;
    public double borderCenterZ = 0;
    public double borderSize = 60_000_000d;

    // === Acceso ===
    public boolean publicAccess = true;
    public List<String> whitelist = new ArrayList<>();   // UUIDs o nombres
    public List<String> blacklist = new ArrayList<>();
    public String accessPermission = "";                  // ej. "etcworlds.world.lobby"

    // === Vinculación de dimensiones ===
    public String linkedNether = ""; // qué mundo se usa como nether desde aquí
    public String linkedEnd = "";    // qué mundo se usa como end desde aquí

    // === Avanzado ===
    public boolean keepLoaded = false;            // ignora idle-unload
    public boolean isTemplate = false;            // marcado como plantilla clonable
    public boolean perPlayerInstance = false;     // si true, cada jugador tiene su propio mundo basado en esta plantilla
    public String worldGroup = "default";          // grupo de inventario/xp/gm compartido
    public List<String> commandWhitelist = new ArrayList<>();
    public List<String> commandBlacklist = new ArrayList<>();

    // === Backups ===
    public boolean backupEnabled = true;

    public static WorldRules fromConfig(String name, ConfigurationSection s) {
        WorldRules r = new WorldRules();
        r.name = name;
        if (s == null) return r;

        r.template = WorldTemplate.fromString(s.getString("template", "NORMAL"));
        r.environment = parseEnv(s.getString("environment", "NORMAL"));
        r.ambient = parseEnv(s.getString("ambient", s.getString("environment", "NORMAL")));
        r.seed = s.getLong("seed", 0L);
        r.generateStructures = s.getBoolean("generate-structures", true);
        r.generatorId = s.getString("generator-id", "");
        r.biomeForce = s.getString("biome-force", "");
        r.customMinY = s.getInt("custom-min-y", Integer.MIN_VALUE);
        r.customMaxY = s.getInt("custom-max-y", Integer.MIN_VALUE);
        r.datapackName = s.getString("datapack-name", "");

        ConfigurationSection sp = s.getConfigurationSection("spawn");
        if (sp != null) {
            r.spawnX = sp.getDouble("x", r.spawnX);
            r.spawnY = sp.getDouble("y", r.spawnY);
            r.spawnZ = sp.getDouble("z", r.spawnZ);
            r.spawnYaw = (float) sp.getDouble("yaw", r.spawnYaw);
            r.spawnPitch = (float) sp.getDouble("pitch", r.spawnPitch);
        }
        r.keepSpawnInMemory = s.getBoolean("keep-spawn-in-memory", false);

        String gm = s.getString("gamemode", "");
        if (gm != null && !gm.isBlank() && !"FREE".equalsIgnoreCase(gm)) {
            try { r.gamemode = GameMode.valueOf(gm.trim().toUpperCase()); } catch (Exception ignored) {}
        }

        r.pvp = s.getBoolean("pvp", true);
        r.mobSpawn = s.getBoolean("mob-spawn", true);
        r.animalSpawn = s.getBoolean("animal-spawn", true);
        r.keepInventoryOnDeath = s.getBoolean("keep-inventory-on-death", false);
        r.fallDamage = s.getBoolean("fall-damage", true);
        r.weatherEnabled = s.getBoolean("weather-enabled", true);
        r.weatherLocked = s.getBoolean("weather-locked", false);
        r.timeLocked = s.getBoolean("time-locked", false);
        r.fixedTimeTick = s.getLong("fixed-time-tick", 6000);
        r.fly = s.getBoolean("force-fly", false);
        r.buildEnabled = s.getBoolean("build-enabled", true);
        r.hunger = s.getBoolean("hunger", true);
        r.autoSave = s.getBoolean("auto-save", true);
        r.viewDistance = s.getInt("view-distance", -1);
        r.simulationDistance = s.getInt("simulation-distance", -1);

        ConfigurationSection b = s.getConfigurationSection("border");
        if (b != null) {
            r.borderCenterX = b.getDouble("center-x", 0);
            r.borderCenterZ = b.getDouble("center-z", 0);
            r.borderSize = b.getDouble("size", 60_000_000d);
        }

        ConfigurationSection ac = s.getConfigurationSection("access");
        if (ac != null) {
            r.publicAccess = ac.getBoolean("public", true);
            r.whitelist = new ArrayList<>(ac.getStringList("whitelist"));
            r.blacklist = new ArrayList<>(ac.getStringList("blacklist"));
            r.accessPermission = ac.getString("permission", "");
        }

        ConfigurationSection ln = s.getConfigurationSection("links");
        if (ln != null) {
            r.linkedNether = ln.getString("nether", "");
            r.linkedEnd = ln.getString("end", "");
        }

        r.keepLoaded = s.getBoolean("keep-loaded", false);
        r.isTemplate = s.getBoolean("is-template", false);
        r.perPlayerInstance = s.getBoolean("per-player-instance", false);
        r.worldGroup = s.getString("world-group", "default");
        r.commandWhitelist = new ArrayList<>(s.getStringList("commands.whitelist"));
        r.commandBlacklist = new ArrayList<>(s.getStringList("commands.blacklist"));
        r.backupEnabled = s.getBoolean("backup.enabled", true);

        return r;
    }

    public void writeTo(ConfigurationSection s) {
        s.set("template", template.name());
        s.set("environment", environment.name());
        s.set("ambient", ambient.name());
        s.set("seed", seed);
        s.set("generate-structures", generateStructures);
        s.set("generator-id", generatorId);
        s.set("biome-force", biomeForce);
        if (customMinY != Integer.MIN_VALUE) s.set("custom-min-y", customMinY);
        if (customMaxY != Integer.MIN_VALUE) s.set("custom-max-y", customMaxY);
        s.set("datapack-name", datapackName);

        s.set("spawn.x", spawnX);
        s.set("spawn.y", spawnY);
        s.set("spawn.z", spawnZ);
        s.set("spawn.yaw", spawnYaw);
        s.set("spawn.pitch", spawnPitch);
        s.set("keep-spawn-in-memory", keepSpawnInMemory);

        s.set("gamemode", gamemode == null ? "FREE" : gamemode.name());
        s.set("pvp", pvp);
        s.set("mob-spawn", mobSpawn);
        s.set("animal-spawn", animalSpawn);
        s.set("keep-inventory-on-death", keepInventoryOnDeath);
        s.set("fall-damage", fallDamage);
        s.set("weather-enabled", weatherEnabled);
        s.set("weather-locked", weatherLocked);
        s.set("time-locked", timeLocked);
        s.set("fixed-time-tick", fixedTimeTick);
        s.set("force-fly", fly);
        s.set("build-enabled", buildEnabled);
        s.set("hunger", hunger);
        s.set("auto-save", autoSave);
        s.set("view-distance", viewDistance);
        s.set("simulation-distance", simulationDistance);

        s.set("border.center-x", borderCenterX);
        s.set("border.center-z", borderCenterZ);
        s.set("border.size", borderSize);

        s.set("access.public", publicAccess);
        s.set("access.whitelist", whitelist);
        s.set("access.blacklist", blacklist);
        s.set("access.permission", accessPermission);

        s.set("links.nether", linkedNether);
        s.set("links.end", linkedEnd);

        s.set("keep-loaded", keepLoaded);
        s.set("is-template", isTemplate);
        s.set("per-player-instance", perPlayerInstance);
        s.set("world-group", worldGroup);
        s.set("commands.whitelist", commandWhitelist);
        s.set("commands.blacklist", commandBlacklist);
        s.set("backup.enabled", backupEnabled);
    }

    public Location spawnLocation(World w) {
        if (w == null) return null;
        return new Location(w, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
    }

    private static World.Environment parseEnv(String s) {
        if (s == null) return World.Environment.NORMAL;
        try { return World.Environment.valueOf(s.trim().toUpperCase()); }
        catch (Exception e) { return World.Environment.NORMAL; }
    }
}
