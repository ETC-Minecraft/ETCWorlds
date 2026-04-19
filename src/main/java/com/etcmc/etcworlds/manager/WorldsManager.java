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
                for (String key : ws.getKeys(false)) {
                    ConfigurationSection sub = ws.getConfigurationSection(key);
                    // Formato nuevo: worlds.<Categoria>.<nombre>.folder
                    // Formato viejo: worlds.<nombre>.folder
                    if (sub != null && sub.getString("folder") == null && !sub.getKeys(false).isEmpty()) {
                        // categoria
                        for (String name : sub.getKeys(false)) {
                            String folder = sub.getString(name + ".folder", dirName + "/" + name);
                            registryFolder.put(name, folder);
                            loadRulesFor(name, folder);
                        }
                    } else {
                        String folder = ws.getString(key + ".folder", dirName + "/" + key);
                        registryFolder.put(key, folder);
                        loadRulesFor(key, folder);
                    }
                }
            }
        }
    }

    /**
     * Decide la categoria con la que se guarda un mundo en worlds-registry.yml.
     * - Nativos: world / world_nether / world_the_end o cualquier mundo cuya carpeta
     *   este en la raiz del world container (sin subcarpetas).
     * - PocketWorlds: carpeta dentro de "pocketworld" (mundos/pocketworld/...).
     * - Creados: el resto.
     */
    private String categoryFor(String name, String folderPath) {
        String norm = folderPath == null ? "" : folderPath.replace('\\', '/');
        if (norm.contains("/pocketworld/") || norm.startsWith("pocketworld/")) return "PocketWorlds";
        if (name.equals("world") || name.equals("world_nether") || name.equals("world_the_end")) return "Nativos";
        if (!norm.contains("/")) return "Nativos";
        return "Creados";
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
        // Auto-registrar todos los mundos que Bukkit ya tiene cargados (world, world_nether, Lobby, etc.)
        for (World w : Bukkit.getWorlds()) {
            String name = w.getName();
            if (!registryFolder.containsKey(name)) {
                registryFolder.put(name, name);
                WorldRules r = new WorldRules();
                r.name = name;
                r.environment = w.getEnvironment();
                r.ambient = w.getEnvironment();
                rules.put(name, r);
                plugin.getLogger().info("Auto-registrado mundo existente: " + name);
            }
        }
        saveRegistry();

        List<String> keepLoaded = plugin.getConfig().getStringList("keep-loaded");
        for (Map.Entry<String, String> e : new java.util.HashMap<>(registryFolder).entrySet()) {
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
        for (Map.Entry<String, String> e : registryFolder.entrySet()) {
            String cat = categoryFor(e.getKey(), e.getValue());
            y.set("worlds." + cat + "." + e.getKey() + ".folder", e.getValue());
        }
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
        return createWorld(name, r, null);
    }

    /**
     * Crea un mundo dentro de un subdirectorio opcional bajo {@code worlds-folder}.
     * Por ejemplo, con {@code worlds-folder=mundos} y {@code subfolder=pocketworld},
     * el mundo se crea en {@code mundos/pocketworld/<name>}.
     * Si {@code subfolder} es {@code null} o blanco, se usa {@code mundos/<name>} como antes.
     */
    public synchronized World createWorld(String name, WorldRules r, String subfolder) throws IllegalStateException {
        if (Bukkit.getWorld(name) != null)
            throw new IllegalStateException("Ya existe un mundo cargado con ese nombre.");
        if (registryFolder.containsKey(name))
            throw new IllegalStateException("Ya existe un mundo registrado con ese nombre.");

        // Carpeta destino dentro de worlds-folder (+ subfolder opcional)
        String dirName = plugin.getConfig().getString("worlds-folder", "mundos");
        String base = (subfolder == null || subfolder.isBlank()) ? dirName : dirName + "/" + subfolder;
        String folderPath = base + "/" + name;
        File targetDir = new File(Bukkit.getWorldContainer(), folderPath);
        if (targetDir.exists())
            throw new IllegalStateException("La carpeta destino ya existe: " + targetDir);
        // Asegurar que el subdirectorio padre existe
        File parent = targetDir.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            plugin.getLogger().warning("[ETCWorlds] No se pudo crear carpeta padre: " + parent);

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
            throw new IllegalStateException("Bukkit/Folia no pudo crear el mundo. Revisa los logs.");
        }

        // Guardar spawn por defecto al centro
        if (r.spawnX == 0.5 && r.spawnY == 80 && r.spawnZ == 0.5) {
            Location s = w.getSpawnLocation();
            r.spawnX = s.getX(); r.spawnY = s.getY(); r.spawnZ = s.getZ();
        }
        applyRules(w, r);
        saveRules(name);
        saveRegistry();
        saveToBukkitYml(name, folderPath, r);
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

        // En Folia, Bukkit.createWorld() lanza UnsupportedOperationException.
        // Usamos NMS directo (FoliaWorldFactory) para crear el ServerLevel.
        if (FoliaWorldFactory.isFolia()) {
            return FoliaWorldFactory.createWorld(plugin, wc);
        }

        try {
            return Bukkit.createWorld(wc);
        } catch (UnsupportedOperationException ignored) {
            return null;
        }
    }

    private WorldType mapType(WorldTemplate t) {
        return switch (t) {
            case FLAT -> WorldType.FLAT;
            case AMPLIFIED -> WorldType.AMPLIFIED;
            case LARGE_BIOMES -> WorldType.LARGE_BIOMES;
            default -> WorldType.NORMAL;
        };
    }

    /**
     * Escribe la entrada del mundo en bukkit.yml para que el servidor lo cargue
     * automáticamente al reiniciar y llame a getDefaultWorldGenerator().
     * Solo se aplica cuando el mundo usa un generador custom (no templates Vanilla).
     */
    private void saveToBukkitYml(String name, String folderPath, WorldRules r) {
        if (r.template == WorldTemplate.FLAT || r.template == WorldTemplate.AMPLIFIED
                || r.template == WorldTemplate.LARGE_BIOMES || r.template == WorldTemplate.NORMAL) {
            if (r.generatorId.isEmpty()) return; // sin generador custom, no es necesario
        }
        try {
            File bukkitYml = new File(org.bukkit.Bukkit.getWorldContainer().getParent(), "bukkit.yml");
            if (!bukkitYml.exists()) return;
            org.bukkit.configuration.file.YamlConfiguration cfg =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(bukkitYml);
            String key = "worlds." + folderPath + ".generator";
            if (!cfg.contains(key)) {
                String genValue = "ETCWorlds" + (r.generatorId.isEmpty() ? "" : ":" + r.generatorId);
                cfg.set(key, genValue);
                cfg.save(bukkitYml);
                plugin.getLogger().info("[ETCWorlds] Registrado en bukkit.yml: " + key + " = " + genValue);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("[ETCWorlds] No se pudo escribir bukkit.yml: " + ex.getMessage());
        }
    }

    public synchronized boolean deleteWorld(String name) {
        World w = Bukkit.getWorld(name);
        if (w != null) {
            World fb = Bukkit.getWorlds().get(0);
            Location fbSpawn = fb.getSpawnLocation();
            for (Player p : new ArrayList<>(w.getPlayers()))
                p.teleportAsync(fbSpawn); // async es legal en cualquier hilo en Folia

            boolean unloaded;
            if (FoliaWorldFactory.isFolia()) {
                // Bukkit.unloadWorld lanza UnsupportedOperationException en Folia.
                // Usamos NMS directo para descargar.
                unloaded = FoliaWorldFactory.unloadWorld(plugin, w, false);
            } else {
                try {
                    unloaded = Bukkit.unloadWorld(w, false);
                } catch (UnsupportedOperationException ex) {
                    plugin.getLogger().warning("[ETCWorlds] unloadWorld no soportado: " + ex.getMessage());
                    return false;
                }
            }
            if (!unloaded) {
                plugin.getLogger().warning("[ETCWorlds] No se pudo descargar '" + name + "'.");
                return false;
            }
        }
        File dir = worldDirOf(name);
        rules.remove(name);
        registryFolder.remove(name);
        saveRegistry();
        removeFromBukkitYml(name);
        if (dir != null && dir.exists()) return WorldFiles.deleteRecursive(dir);
        return true;
    }

    /**
     * Quita el mundo del registro de ETCWorlds y de bukkit.yml SIN borrar archivos
     * y SIN intentar descargarlo. Sirve para "olvidar" un mundo que no se pudo unload
     * (Folia) y permitir crear otro con nombre distinto en su lugar.
     */
    public synchronized void forgetWorld(String name) {
        String resolved = resolveWorldName(name);
        if (resolved == null) return;
        rules.remove(resolved);
        registryFolder.remove(resolved);
        saveRegistry();
        removeFromBukkitYml(resolved);
    }

    /** Borra la entrada worlds.<folderPath>.generator de bukkit.yml para evitar reload al reiniciar. */
    private void removeFromBukkitYml(String name) {
        try {
            File bukkitYml = new File(Bukkit.getWorldContainer().getParent(), "bukkit.yml");
            if (!bukkitYml.exists()) return;
            org.bukkit.configuration.file.YamlConfiguration cfg =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(bukkitYml);
            org.bukkit.configuration.ConfigurationSection ws = cfg.getConfigurationSection("worlds");
            if (ws == null) return;
            boolean changed = false;
            for (String key : new ArrayList<>(ws.getKeys(false))) {
                String simple = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
                if (simple.equalsIgnoreCase(name)) {
                    ws.set(key, null);
                    changed = true;
                }
            }
            if (changed) cfg.save(bukkitYml);
        } catch (Exception ex) {
            plugin.getLogger().warning("[ETCWorlds] No se pudo limpiar bukkit.yml: " + ex.getMessage());
        }
    }

    public synchronized World loadWorld(String name) {
        // Case-insensitive: primero busca por nombre exacto en Bukkit, luego resuelve alias
        World existing = Bukkit.getWorld(name);
        if (existing != null) return existing;
        // Intenta con nombre canónico registrado
        String canonical = resolveWorldName(name);
        if (canonical != null && !canonical.equals(name)) {
            existing = Bukkit.getWorld(canonical);
            if (existing != null) return existing;
        }
        String resolved = canonical != null ? canonical : name;
        WorldRules r = rules.get(resolved);
        String folder = registryFolder.get(resolved);
        if (folder == null) return null;
        if (r == null) { r = new WorldRules(); r.name = resolved; rules.put(resolved, r); }
        World w = buildAndCreate(name, folder, r);
        if (w != null) applyRules(w, r);
        return w;
    }

    public synchronized boolean unloadWorld(String name, boolean save) {
        World w = Bukkit.getWorld(name);
        if (w == null) return true;
        if (!w.getPlayers().isEmpty()) return false;
        if (FoliaWorldFactory.isFolia()) {
            return FoliaWorldFactory.unloadWorld(plugin, w, save);
        }
        try {
            return Bukkit.unloadWorld(w, save);
        } catch (UnsupportedOperationException foliaEx) {
            plugin.getLogger().warning(
                "[ETCWorlds] Folia no soporta descarga de mundos en tiempo real. "
                + "El mundo '" + name + "' seguirá cargado hasta que se reinicie el servidor.");
            return false;
        }
    }

    public synchronized boolean importExisting(String name, String relativeFolder) {
        if (registryFolder.containsKey(name)) return false;
        File source = new File(Bukkit.getWorldContainer(), relativeFolder);
        if (!source.exists() || !new File(source, "level.dat").exists()) return false;

        // Si el mundo NO esta ya bajo worlds-folder, lo movemos fisicamente alli.
        // Asi tras importar /ecw import MundoRaiz queda en mundos/MundoRaiz.
        String dirName = plugin.getConfig().getString("worlds-folder", "mundos");
        String targetRel = dirName + "/" + name;
        File target = new File(Bukkit.getWorldContainer(), targetRel);

        boolean alreadyInPlace;
        try {
            alreadyInPlace = source.getCanonicalFile().equals(target.getCanonicalFile());
        } catch (IOException ex) {
            alreadyInPlace = source.getAbsoluteFile().equals(target.getAbsoluteFile());
        }

        if (!alreadyInPlace) {
            if (target.exists()) {
                plugin.getLogger().warning("[ETCWorlds] Import: ya existe destino " + target);
                return false;
            }
            // Si el mundo esta cargado en Bukkit (improbable al importar), abortar.
            if (Bukkit.getWorld(name) != null) {
                plugin.getLogger().warning("[ETCWorlds] Import: el mundo " + name + " esta cargado, no se puede mover.");
                return false;
            }
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("[ETCWorlds] Import: no se pudo crear " + parent);
                return false;
            }
            try {
                java.nio.file.Files.move(source.toPath(), target.toPath());
                plugin.getLogger().info("[ETCWorlds] Import: movido " + source + " -> " + target);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING,
                        "[ETCWorlds] Import: no se pudo mover " + source + " a " + target, ex);
                return false;
            }
            relativeFolder = targetRel;
        }

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
        w.setGameRule(org.bukkit.GameRule.DO_IMMEDIATE_RESPAWN, r.immediateRespawn);
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
        double size = Math.max(1.0d, Math.min(r.borderSize, 5.9999968E7d));
        wb.setSize(size);
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

    /**
     * Resuelve el nombre canónico del mundo: primero intenta coincidencia exacta,
     * luego case-insensitive. Devuelve null si no está registrado.
     */
    public String resolveWorldName(String input) {
        if (input == null) return null;
        if (registryFolder.containsKey(input)) return input;
        for (String key : registryFolder.keySet())
            if (key.equalsIgnoreCase(input)) return key;
        return null;
    }

    public WorldRules getRules(String name) {
        String resolved = resolveWorldName(name);
        return resolved != null ? rules.get(resolved) : null;
    }
    public boolean isManaged(String name) { return resolveWorldName(name) != null; }
    public Collection<String> getManagedNames() { return registryFolder.keySet(); }
    public Map<String, String> getRegistry() { return new HashMap<>(registryFolder); }
    public File getWorldsFolder() { return worldsFolder; }

    public File worldDirOf(String name) {
        String resolved = resolveWorldName(name);
        if (resolved == null) return null;
        String rel = registryFolder.get(resolved);
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
