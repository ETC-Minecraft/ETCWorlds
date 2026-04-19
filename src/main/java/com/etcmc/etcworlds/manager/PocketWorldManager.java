package com.etcmc.etcworlds.manager;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.WorldRules;
import com.etcmc.etcworlds.model.WorldTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Gestiona los "PocketWorld" personales de cada jugador.
 *
 * Cada jugador puede tener UN pocketworld:
 *   - mundo VOID con worldborder 1000x1000 centrado en (0,0)
 *   - whitelist solo el dueño + invitados temporales
 *   - nombre: pw_&lt;uuid8&gt;
 *
 * Persistencia: plugins/ETCWorlds/pocketworlds.yml
 */
public class PocketWorldManager {

    public static final String WORLD_PREFIX = "pw_";

    private final ETCWorlds plugin;
    private final Map<UUID, PocketWorld> byOwner = new ConcurrentHashMap<>();
    private final Map<String, UUID> byWorldName = new ConcurrentHashMap<>(); // worldName -> owner
    private File storageFile;

    public PocketWorldManager(ETCWorlds plugin) { this.plugin = plugin; }

    // ===========================================================================================
    //   MODELO
    // ===========================================================================================

    public static class PocketWorld {
        public final UUID owner;
        public final String worldName;
        public final long createdAt;
        public final Set<UUID> invitees;

        public PocketWorld(UUID owner, String worldName, long createdAt, Set<UUID> invitees) {
            this.owner = owner;
            this.worldName = worldName;
            this.createdAt = createdAt;
            this.invitees = invitees != null ? invitees : new HashSet<>();
        }
    }

    // ===========================================================================================
    //   PERSISTENCIA
    // ===========================================================================================

    public void load() {
        plugin.getDataFolder().mkdirs();
        storageFile = new File(plugin.getDataFolder(), "pocketworlds.yml");
        byOwner.clear();
        byWorldName.clear();
        if (!storageFile.exists()) return;

        YamlConfiguration y = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection root = y.getConfigurationSection("pocketworlds");
        if (root == null) return;
        for (String uuidStr : root.getKeys(false)) {
            try {
                UUID owner = UUID.fromString(uuidStr);
                String world = root.getString(uuidStr + ".world");
                long created = root.getLong(uuidStr + ".created", System.currentTimeMillis());
                Set<UUID> invitees = new HashSet<>();
                for (String s : root.getStringList(uuidStr + ".invitees")) {
                    try { invitees.add(UUID.fromString(s)); } catch (Exception ignored) {}
                }
                if (world == null || world.isBlank()) continue;
                PocketWorld pw = new PocketWorld(owner, world, created, invitees);
                byOwner.put(owner, pw);
                byWorldName.put(world.toLowerCase(), owner);
            } catch (Exception ex) {
                plugin.getLogger().warning("[PocketWorld] entrada inválida en pocketworlds.yml: " + uuidStr);
            }
        }
        plugin.getLogger().info("[PocketWorld] " + byOwner.size() + " pocketworlds cargados.");
    }

    public synchronized void save() {
        if (storageFile == null) storageFile = new File(plugin.getDataFolder(), "pocketworlds.yml");
        YamlConfiguration y = new YamlConfiguration();
        for (PocketWorld pw : byOwner.values()) {
            String key = "pocketworlds." + pw.owner.toString() + ".";
            y.set(key + "world", pw.worldName);
            y.set(key + "created", pw.createdAt);
            List<String> inv = new ArrayList<>();
            for (UUID u : pw.invitees) inv.add(u.toString());
            y.set(key + "invitees", inv);
        }
        try { y.save(storageFile); }
        catch (IOException ex) { plugin.getLogger().log(Level.WARNING, "No se pudo guardar pocketworlds.yml", ex); }
    }

    // ===========================================================================================
    //   API
    // ===========================================================================================

    public PocketWorld get(UUID owner) { return byOwner.get(owner); }

    public PocketWorld getByWorldName(String worldName) {
        if (worldName == null) return null;
        UUID o = byWorldName.get(worldName.toLowerCase());
        return o != null ? byOwner.get(o) : null;
    }

    public boolean exists(UUID owner) { return byOwner.containsKey(owner); }

    public Collection<PocketWorld> all() { return Collections.unmodifiableCollection(byOwner.values()); }

    public boolean isPocketWorld(String worldName) {
        return worldName != null && worldName.toLowerCase().startsWith(WORLD_PREFIX);
    }

    /** Invita al jugador targetUuid al pocketworld del owner. */
    public boolean invite(UUID owner, UUID target) {
        PocketWorld pw = byOwner.get(owner);
        if (pw == null) return false;
        if (pw.invitees.add(target)) {
            // Reflejar en el WorldRules.whitelist para que WorldAccessListener lo permita
            WorldRules r = plugin.worlds().getRules(pw.worldName);
            if (r != null && !r.whitelist.contains(target.toString())) {
                r.whitelist.add(target.toString());
                plugin.worlds().saveRules(pw.worldName);
            }
            save();
            return true;
        }
        return false;
    }

    public boolean uninvite(UUID owner, UUID target) {
        PocketWorld pw = byOwner.get(owner);
        if (pw == null) return false;
        if (pw.invitees.remove(target)) {
            WorldRules r = plugin.worlds().getRules(pw.worldName);
            if (r != null) {
                r.whitelist.remove(target.toString());
                plugin.worlds().saveRules(pw.worldName);
            }
            save();
            return true;
        }
        return false;
    }

    public boolean isInvited(UUID owner, UUID target) {
        PocketWorld pw = byOwner.get(owner);
        return pw != null && pw.invitees.contains(target);
    }

    /**
     * Crea el pocketworld para el jugador. callback recibe el World creado o null si falla.
     * DEBE invocarse — internamente despacha al global region scheduler (Folia-safe).
     */
    public void create(Player owner, Consumer<World> callback) {
        UUID uuid = owner.getUniqueId();
        if (byOwner.containsKey(uuid)) {
            World w = Bukkit.getWorld(byOwner.get(uuid).worldName);
            if (w != null) { callback.accept(w); return; }
            // existe en registro pero no está cargado → cargar
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
                World loaded = plugin.worlds().loadWorld(byOwner.get(uuid).worldName);
                callback.accept(loaded);
            });
            return;
        }
        String shortId = uuid.toString().replace("-", "").substring(0, 8);
        String worldName = WORLD_PREFIX + shortId;

        int border = plugin.getConfig().getInt("pocketworlds.border-size", 1000);

        WorldRules r = new WorldRules();
        r.name = worldName;
        r.template = WorldTemplate.VOID;
        r.environment = World.Environment.NORMAL;
        r.ambient = World.Environment.NORMAL;
        r.generatorId = ""; // VoidGenerator default = con plataforma 16x16 en (0,64,0)
        r.spawnX = 0.5; r.spawnY = 65; r.spawnZ = 0.5;
        r.spawnYaw = 0; r.spawnPitch = 0;
        r.borderCenterX = 0; r.borderCenterZ = 0;
        r.borderSize = border;
        r.publicAccess = false;
        r.whitelist.add(uuid.toString());
        r.pvp = false;
        r.mobSpawn = false;
        r.animalSpawn = false;
        r.keepLoaded = false;
        r.isTemplate = false;
        r.perPlayerInstance = false;
        r.fly = plugin.getConfig().getBoolean("pocketworlds.fly", true);
        r.fallDamage = plugin.getConfig().getBoolean("pocketworlds.fall-damage", false);
        String enter = plugin.getConfig().getString("pocketworlds.enter-message",
                "&aBienvenido a tu PocketWorld, &e{player}&a.");
        r.enterMessage = enter.replace("{player}", owner.getName());

        Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
            try {
                long t0 = System.currentTimeMillis();
                World w = plugin.worlds().createWorld(worldName, r);
                if (w == null) { callback.accept(null); return; }
                PocketWorld pw = new PocketWorld(uuid, worldName, System.currentTimeMillis(), new HashSet<>());
                byOwner.put(uuid, pw);
                byWorldName.put(worldName.toLowerCase(), uuid);
                save();
                plugin.getLogger().info("[PocketWorld] Creado " + worldName + " para " + owner.getName()
                        + " en " + (System.currentTimeMillis() - t0) + "ms");
                callback.accept(w);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Error creando pocketworld para " + owner.getName(), ex);
                callback.accept(null);
            }
        });
    }

    /** Borra el pocketworld del jugador (mundo + entrada). */
    public boolean delete(UUID owner) {
        PocketWorld pw = byOwner.remove(owner);
        if (pw == null) return false;
        byWorldName.remove(pw.worldName.toLowerCase());
        save();
        // Borra el mundo (incluye archivos en disco). deleteWorld saca jugadores y descarga.
        try {
            plugin.worlds().deleteWorld(pw.worldName);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudo borrar pocketworld " + pw.worldName, ex);
            return false;
        }
        return true;
    }

    /** Devuelve el spawn calculado del pocketworld (sin cargarlo). */
    public Location spawnOf(PocketWorld pw) {
        if (pw == null) return null;
        World w = Bukkit.getWorld(pw.worldName);
        WorldRules r = plugin.worlds().getRules(pw.worldName);
        if (w != null && r != null) return r.spawnLocation(w);
        return null;
    }
}
