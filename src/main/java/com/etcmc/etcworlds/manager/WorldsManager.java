package com.etcmc.etcworlds.manager;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.generator.FloatingIslandsGenerator;
import com.etcmc.etcworlds.generator.LayeredVoidGenerator;
import com.etcmc.etcworlds.generator.OneblockGenerator;
import com.etcmc.etcworlds.generator.SingleBiomeProvider;
import com.etcmc.etcworlds.generator.SkyblockGenerator;
import com.etcmc.etcworlds.generator.VoidGenerator;
import com.etcmc.etcworlds.model.WorldRules;
import com.etcmc.etcworlds.model.WorldTemplate;
import com.etcmc.etcworlds.util.DatapackHeightGenerator;
import com.etcmc.etcworlds.util.WorldFiles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Núcleo del plugin. Mantiene el registro de mundos administrados, su WorldRules,
 * y maneja creación / carga / descarga / eliminación.
 *
 * Persistencia:
 *   - registry global: plugins/ETCWorlds/worlds-registry.yml (lista de mundos + carpeta + estado)
 *   - reglas por mundo: <worlds-folder>/<nombre>/etcworlds.yml
 */
public class WorldsManager {

    private final ETCWorlds plugin;
    private final Map<String, WorldRules> rules = new ConcurrentHashMap<>();
    private final Map<String, String> registryFolder = new ConcurrentHashMap<>(); // worldName -> path relativo
    private File registryFile;
    private File worldsFolder;

    public WorldsManager(ETCWorlds plugin) {
        this.plugin = plugin;
    }

    // ===========================================================================================
    //   CARGA / RECARGA
    // ===========================================================================================

    public void loadRegistry() {
        plugin.reloadConfig();
        String dirName = plugin.getConfig().getString("worlds-folder", "mundos");
        this.worldsFolder = new File(Bukkit.getWorldContainer(), dirName);
        if (!worldsFolder.exists() && !worldsFolder.mkdirs())
            plugin.getLogger().warning("No se pudo crear carpeta de mundos: " + worldsFolder);

        this.registryFile = new File(plugin.getDataFolder(), "worlds-registry.yml");
        plugin.getDataFolder().mkdirs();
        registryFolder.clear();
        rules.clear();

        if (registryFile.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(registryFile);
            ConfigurationSection ws = y.getConfigurationSection("worlds");
            if (ws != null) {
                for (String name : ws.getKeys(false)) {
                    String folder = ws.getString(name + ".folder", dirName + "/" + name);
                    registryFolder.put(name, folder);
                    loadRulesFor(name, folder);
                }
            }
        }
    }

    private void loadRulesFor(String name, String folderPath) {
        File worldDir = new File(Bukkit.getWorldContainer(), folderPath);
        File rulesFile = new File(worldDir, "etcworlds.yml");
        WorldRules r;
        if (rulesFile.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(rulesFile);
            r = WorldRules.fromConfig(name, y);
        } else {
            r = new WorldRules();
            r.name = name;
        }
        rules.put(name, r);
    }

    /** Carga al inicio los mundos persistidos que no estén ya cargados (los vanilla los maneja Bukkit). */
    public void loadStartupWorlds() {
        List<String> keepLoaded = plugin.getConfig().getStringList("keep-loaded");
        for (Map.Entry<String, String> e : registryFolder.entrySet()) {
            String name = e.getKey();
            if (Bukkit.getWorld(name) != null) continue;
            WorldRules r = rules.get(name);
            if (r == null) continue;
            if (r.keepLoaded || keepLoaded.contains(name)) {
                try { loadWorld(name); }
                catch (Exception ex) { plugin.getLogger().log(Level.WARNING, "No se pudo cargar mundo " + name, ex); }
            }
        }
    }

    public void saveRegistry() {
        if (registryFile == null) return;
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, String> e : registryFolder.entrySet())
            y.set("worlds." + e.getKey() + ".folder", e.getValue());
        try { y.save(registryFile); }
        catch (IOException ex) { plugin.getLogger().log(Level.WARNING, "No se pudo guardar registry", ex); }
    }

    public void saveRules(String name) {
        WorldRules r = rules.get(name);
        if (r == null) return;
        File worldDir = worldDirOf(name);
        if (worldDir == null) return;
        File f = new File(worldDir, "etcworlds.yml");
        YamlConfiguration y = new YamlConfiguration();
        r.writeTo(y);
        try { f.getParentFile().mkdirs(); y.save(f); }
        catch (IOException ex) { plugin.getLogger().log(Level.WARNING, "No se pudo guardar reglas para " + name, ex); }
    }

    public void shutdown() {
        for (String name : registryFolder.keySet()) saveRules(name);
        saveRegistry();
    }

    // ===========================================================================================
    //   CRUD
    // ===========================================================================================

    public synchronized World createWorld(String name, WorldRules r) throws IllegalStateException {
        if (Bukkit.getWorld(name) != null)
            throw new IllegalStateException("Ya existe un mundo cargado con ese nombre.");
        if (registryFolder.containsKey(name))
            throw new IllegalStateException("Ya existe un mundo registrado con ese nombre.");

        // Carpeta destino dentro de worlds-folder
        String dirName = plugin.getConfig().getString("worlds-folder", "mundos");
        String folderPath = dirName + "/" + name;
        File targetDir = new File(Bukkit.getWorldContainer(), folderPath);
        if (targetDir.exists())
            throw new IllegalStateException("La carpeta destino ya existe: " + targetDir);

        // Datapack altura custom
        if (r.customMinY != Integer.MIN_VALUE && r.customMaxY != Integer.MIN_VALUE) {
            try {
                int height = r.customMaxY - r.customMinY;
                String dpId = DatapackHeightGenerator.write(targetDir, name, r.customMinY, height);
                r.datapackName = dpId;
                plugin.getLogger().info("Datapack altura custom escrito: " + dpId);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "No se pudo escribir datapack de altura", ex);
            }
        }

        r.name = name;
        rules.put(name, r);
        registryFolder.put(name, folderPath);

        World w = buildAndCreate(name, folderPath, r);
        if (w == null) {
            rules.remove(name);
            registryFolder.remove(name);
            throw new IllegalStateException("Bukkit no pudo crear el mundo.");
        }

        // Guardar spawn por defecto al centro
        if (r.spawnX == 0.5 && r.spawnY == 80 && r.spawnZ == 0.5) {
            Location s = w.getSpawnLocation();
            r.spawnX = s.getX(); r.spawnY = s.getY(); r.spawnZ = s.getZ();
        }
        applyRules(w, r);
        saveRules(name);
        saveRegistry();
        return w;
    }

    private World buildAndCreate(String name, String folderPath, WorldRules r) {
        // WorldCreator acepta nombre con barras: usa esa ruta relativa al worldContainer.
        WorldCreator wc = new WorldCreator(folderPath);
        wc.environment(r.environment);
        if (r.seed != 0L) wc.seed(r.seed);
        wc.generateStructures(r.generateStructures);
        wc.type(mapType(r.template));
        ChunkGenerator gen = generatorFor(r);
        if (gen != null) wc.generator(gen);
        BiomeProvider bp = biomeProviderFor(r);
        if (bp != null) wc.biomeProvider(bp);
        return Bukkit.createWorld(wc);
    }

    private WorldType mapType(WorldTemplate t) {
        return switch (t) {
            case FLAT -> WorldType.FLAT;
            case AMPLIFIED -> WorldType.AMPLIFIED;
            case LARGE_BIOMES -> WorldType.LARGE_BIOMES;
            default -> WorldType.NORMAL;
        };
    }

    public synchronized boolean deleteWorld(String name) {
        World w = Bukkit.getWorld(name);
        if (w != null) {
            for (Player p : new ArrayList<>(w.getPlayers())) {
                World fb = Bukkit.getWorlds().get(0);
                p.teleport(fb.getSpawnLocation());
            }
            if (!Bukkit.unloadWorld(w, false)) return false;
        }
        File dir = worldDirOf(name);
        rules.remove(name);
        registryFolder.remove(name);
        saveRegistry();
        if (dir != null && dir.exists()) return WorldFiles.deleteRecursive(dir);
        return true;
    }

    public synchronized World loadWorld(String name) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) return existing;
        WorldRules r = rules.get(name);
        String folder = registryFolder.get(name);
        if (folder == null) return null;
        if (r == null) { r = new WorldRules(); r.name = name; rules.put(name, r); }
        World w = buildAndCreate(name, folder, r);
        if (w != null) applyRules(w, r);
        return w;
    }

    public synchronized boolean unloadWorld(String name, boolean save) {
        World w = Bukkit.getWorld(name);
        if (w == null) return true;
        if (!w.getPlayers().isEmpty()) return false;
        return Bukkit.unloadWorld(w, save);
    }

    public synchronized boolean importExisting(String name, String relativeFolder) {
        if (registryFolder.containsKey(name)) return false;
        File f = new File(Bukkit.getWorldContainer(), relativeFolder);
        if (!f.exists() || !new File(f, "level.dat").exists()) return false;
        registryFolder.put(name, relativeFolder);
        if (!rules.containsKey(name)) {
            WorldRules r = new WorldRules();
            r.name = name;
            rules.put(name, r);
        }
        saveRegistry();
        saveRules(name);
        return true;
    }

    // ===========================================================================================
    //   APLICACIÓN DE REGLAS
    // ===========================================================================================

    public void applyRules(World w, WorldRules r) {
        if (w == null || r == null) return;
        w.setPVP(r.pvp);
        w.setSpawnFlags(r.mobSpawn, r.animalSpawn);
        w.setKeepSpawnInMemory(r.keepSpawnInMemory);
        w.setAutoSave(r.autoSave);
        w.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, r.keepInventoryOnDeath);
        w.setGameRule(org.bukkit.GameRule.FALL_DAMAGE, r.fallDamage);
        w.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, r.weatherEnabled && !r.weatherLocked);
        w.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, !r.timeLocked);
        if (r.timeLocked) w.setTime(r.fixedTimeTick);
        if (r.viewDistance > 0) w.setViewDistance(r.viewDistance);
        if (r.simulationDistance > 0) w.setSimulationDistance(r.simulationDistance);

        // Spawn
        Location sp = r.spawnLocation(w);
        if (sp != null) w.setSpawnLocation(sp);

        // Worldborder
        org.bukkit.WorldBorder wb = w.getWorldBorder();
        wb.setCenter(r.borderCenterX, r.borderCenterZ);
        wb.setSize(r.borderSize);
    }

    // ===========================================================================================
    //   GENERATOR / BIOME PROVIDER RESOLUTION (llamado por Bukkit en getDefaultWorldGenerator)
    // ===========================================================================================

    public ChunkGenerator resolveGenerator(String worldName, String id) {
        WorldRules r = rules.get(worldName);
        if (r == null) {
            // Puede ser un nombre con folder, ej "mundos/x"
            for (Map.Entry<String, String> e : registryFolder.entrySet())
                if (worldName.equalsIgnoreCase(e.getValue())) {
                    r = rules.get(e.getKey()); break;
                }
        }
        if (r == null) return null;
        return generatorFor(r);
    }

    public BiomeProvider resolveBiomeProvider(String worldName, String id) {
        WorldRules r = rules.get(worldName);
        if (r == null) return null;
        return biomeProviderFor(r);
    }

    private ChunkGenerator generatorFor(WorldRules r) {
        return switch (r.template) {
            case VOID -> new VoidGenerator(r.generatorId);
            case SKYBLOCK -> new SkyblockGenerator();
            case ONEBLOCK -> new OneblockGenerator();
            case LAYERED_VOID -> new LayeredVoidGenerator(r.generatorId);
            case FLOATING_ISLANDS -> new FloatingIslandsGenerator();
            default -> null; // vanilla / flat / amplified / single-biome / custom-height usan el generador del environment
        };
    }

    private BiomeProvider biomeProviderFor(WorldRules r) {
        if (r.template == WorldTemplate.SINGLE_BIOME && r.biomeForce != null && !r.biomeForce.isBlank())
            return new SingleBiomeProvider(r.biomeForce);
        if (r.biomeForce != null && !r.biomeForce.isBlank())
            return new SingleBiomeProvider(r.biomeForce);
        return null;
    }

    // ===========================================================================================
    //   QUERIES
    // ===========================================================================================

    public WorldRules getRules(String name) { return rules.get(name); }
    public boolean isManaged(String name) { return registryFolder.containsKey(name); }
    public Collection<String> getManagedNames() { return registryFolder.keySet(); }
    public Map<String, String> getRegistry() { return new HashMap<>(registryFolder); }
    public File getWorldsFolder() { return worldsFolder; }

    public File worldDirOf(String name) {
        String rel = registryFolder.get(name);
        if (rel == null) return null;
        return new File(Bukkit.getWorldContainer(), rel);
    }

    /** Verifica si un jugador puede acceder a un mundo según whitelist/blacklist/permiso. */
    public boolean canAccess(Player p, String worldName) {
        if (p.hasPermission("etcworlds.bypass.access")) return true;
        WorldRules r = rules.get(worldName);
        if (r == null) return true;
        if (!r.accessPermission.isBlank() && !p.hasPermission(r.accessPermission)) return false;
        String uid = p.getUniqueId().toString();
        String nm = p.getName();
        if (!r.blacklist.isEmpty() && (r.blacklist.contains(uid) || r.blacklist.contains(nm))) return false;
        if (!r.publicAccess && !(r.whitelist.contains(uid) || r.whitelist.contains(nm))) return false;
        return true;
    }

    public List<String> linkedNetherTargetsOf(String sourceWorld) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, WorldRules> e : rules.entrySet())
            if (sourceWorld.equalsIgnoreCase(e.getValue().linkedNether)) out.add(e.getKey());
        return out;
    }
}
