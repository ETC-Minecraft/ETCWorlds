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
import java.util.Locale;
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
    private final Set<String> pendingDeletes = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private File storageFile;

    public PocketWorldManager(ETCWorlds plugin) { this.plugin = plugin; }

    // ===========================================================================================
    //   MODELO
    // ===========================================================================================

    public static class PocketWorld {
        public final UUID owner;
        public final String worldName;
        public final long createdAt;
        /** Invitados con acceso de entrada y construccion. */
        public final Set<UUID> invitees;
        /** Usuarios con permiso de editar las rules del pocketworld. Subconjunto logico de invitees. */
        public final Set<UUID> users;

        public PocketWorld(UUID owner, String worldName, long createdAt, Set<UUID> invitees, Set<UUID> users) {
            this.owner = owner;
            this.worldName = worldName;
            this.createdAt = createdAt;
            this.invitees = invitees != null ? invitees : new HashSet<>();
            this.users = users != null ? users : new HashSet<>();
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
        pendingDeletes.clear();
        if (!storageFile.exists()) return;

        YamlConfiguration y = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection root = y.getConfigurationSection("pocketworlds");
        if (root != null) for (String uuidStr : root.getKeys(false)) {
            try {
                UUID owner = UUID.fromString(uuidStr);
                String world = root.getString(uuidStr + ".world");
                long created = root.getLong(uuidStr + ".created", System.currentTimeMillis());
                Set<UUID> invitees = new HashSet<>();
                for (String s : root.getStringList(uuidStr + ".invitees")) {
                    try { invitees.add(UUID.fromString(s)); } catch (Exception ignored) {}
                }
                Set<UUID> users = new HashSet<>();
                for (String s : root.getStringList(uuidStr + ".users")) {
                    try { users.add(UUID.fromString(s)); } catch (Exception ignored) {}
                }
                if (world == null || world.isBlank()) continue;
                PocketWorld pw = new PocketWorld(owner, world, created, invitees, users);
                byOwner.put(owner, pw);
                byWorldName.put(world.toLowerCase(), owner);
            } catch (Exception ex) {
                plugin.getLogger().warning("[PocketWorld] entrada inválida en pocketworlds.yml: " + uuidStr);
            }
        }
        for (String s : y.getStringList("pending-deletes")) pendingDeletes.add(s);
        plugin.getLogger().info("[PocketWorld] " + byOwner.size() + " pocketworlds cargados, "
                + pendingDeletes.size() + " pendientes de borrar.");
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
            List<String> usrs = new ArrayList<>();
            for (UUID u : pw.users) usrs.add(u.toString());
            y.set(key + "users", usrs);
        }
        y.set("pending-deletes", new ArrayList<>(pendingDeletes));
        try { y.save(storageFile); }
        catch (IOException ex) { plugin.getLogger().log(Level.WARNING, "No se pudo guardar pocketworlds.yml", ex); }
    }

    /** Marca un mundo para borrarse en el proximo arranque. */
    public synchronized void markPendingDelete(String worldName) {
        if (worldName == null || worldName.isBlank()) return;
        pendingDeletes.add(worldName);
        save();
    }

    /**
     * Limpia carpeta y registros huerfanos de un nombre de mundo (no debe estar cargado).
     * Pensado para ejecutarse antes de recrear un pocketworld con el mismo nombre canonico.
     */
    private void cleanupOrphan(String worldName, String subfolder) {
        if (Bukkit.getWorld(worldName) != null) return; // sigue cargado, no tocar
        try {
            plugin.worlds().forgetWorld(worldName);
        } catch (Exception ignored) {}
        File parent = new File(Bukkit.getWorldContainer(),
                plugin.getConfig().getString("worlds-folder", "mundos") + "/" + subfolder);
        File f = new File(parent, worldName);
        if (!f.exists()) f = new File(Bukkit.getWorldContainer(),
                plugin.getConfig().getString("worlds-folder", "mundos") + "/" + worldName);
        if (f.exists()) {
            boolean ok = com.etcmc.etcworlds.util.WorldFiles.deleteRecursive(f);
            plugin.getLogger().info("[PocketWorld] Limpieza huerfana de '" + worldName + "' borrado=" + ok);
        }
        if (pendingDeletes.remove(worldName)) save();
    }

    /**
     * Procesa los mundos pendientes de borrar (llamar en onEnable, antes de cargar mundos).
     * Borra carpetas y entradas de bukkit.yml. Si el mundo ya esta cargado por Bukkit
     * (porque seguia en bukkit.yml de un crash anterior), se omite.
     */
    public synchronized void processPendingDeletes() {
        if (pendingDeletes.isEmpty()) return;
        String dir = plugin.getConfig().getString("worlds-folder", "mundos");
        String sub = plugin.getConfig().getString("pocketworlds.subfolder", "pocketworld");
        File parent = new File(Bukkit.getWorldContainer(), dir + "/" + sub);
        File legacyParent = new File(Bukkit.getWorldContainer(), dir);
        java.util.Iterator<String> it = pendingDeletes.iterator();
        while (it.hasNext()) {
            String name = it.next();
            try {
                if (Bukkit.getWorld(name) != null) {
                    plugin.getLogger().warning("[PocketWorld] Pendiente '" + name
                            + "' aun esta cargado, no se puede borrar.");
                    continue;
                }
                plugin.worlds().forgetWorld(name);
                File f = new File(parent, name);
                if (!f.exists()) f = new File(legacyParent, name);
                if (f.exists()) {
                    boolean ok = com.etcmc.etcworlds.util.WorldFiles.deleteRecursive(f);
                    plugin.getLogger().info("[PocketWorld] Pendiente '" + name + "' borrado="
                            + ok + " (" + f.getAbsolutePath() + ")");
                } else {
                    plugin.getLogger().info("[PocketWorld] Pendiente '" + name + "' sin carpeta, limpiado.");
                }
                it.remove();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Error borrando pendiente " + name, ex);
            }
        }
        save();
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

    /** Da permiso al jugador target de editar las rules del pocketworld del owner. */
    public boolean addUser(UUID owner, UUID target) {
        PocketWorld pw = byOwner.get(owner);
        if (pw == null) return false;
        if (pw.users.add(target)) { save(); return true; }
        return false;
    }

    public boolean removeUser(UUID owner, UUID target) {
        PocketWorld pw = byOwner.get(owner);
        if (pw == null) return false;
        if (pw.users.remove(target)) { save(); return true; }
        return false;
    }

    public boolean isUser(UUID owner, UUID target) {
        PocketWorld pw = byOwner.get(owner);
        return pw != null && (pw.owner.equals(target) || pw.users.contains(target));
    }

    /**
     * ¿Puede el jugador {@code player} construir/romper en el mundo {@code worldName}?
     * Devuelve {@code true} si:
     *   - el mundo NO es un pocketworld (este check no aplica), o
     *   - el jugador es el dueño del pocketworld, o
     *   - el jugador esta invitado a ese pocketworld.
     * Para mundos no-pocket retorna true (no es responsabilidad de este manager).
     */
    public boolean canBuild(UUID player, String worldName) {
        if (worldName == null || player == null) return true;
        UUID owner = byWorldName.get(worldName.toLowerCase());
        if (owner == null) return true; // no es pocketworld
        if (owner.equals(player)) return true;
        PocketWorld pw = byOwner.get(owner);
        return pw != null && pw.invitees.contains(player);
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
        final String worldName = generateWorldName(owner);

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
        // Por defecto en pocketworlds: keep-inventory ON (anti-trampas).
        // Solo un admin puede desactivarlo via /pw rules <jugador>.
        r.keepInventoryOnDeath = plugin.getConfig().getBoolean("pocketworlds.default-keep-inventory", true);
        r.immediateRespawn = plugin.getConfig().getBoolean("pocketworlds.default-immediate-respawn", true);
        String enter = plugin.getConfig().getString("pocketworlds.enter-message",
                "&aBienvenido a tu PocketWorld, &e{player}&a.");
        r.enterMessage = enter.replace("{player}", owner.getName());

        Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
            try {
                long t0 = System.currentTimeMillis();
                String subfolder = plugin.getConfig().getString("pocketworlds.subfolder", "pocketworld");
                // Si quedo carpeta o entrada huerfana del mismo nombre, limpiarla antes de crear.
                cleanupOrphan(worldName, subfolder);
                World w = plugin.worlds().createWorld(worldName, r, subfolder);
                if (w == null) { callback.accept(null); return; }
                PocketWorld pw = new PocketWorld(uuid, worldName, System.currentTimeMillis(), new HashSet<>(), new HashSet<>());
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

    /**
     * Genera el nombre canonico del pocketworld del jugador con el formato
     * {@code pw_<usuario>_<uuid8>}. SIEMPRE devuelve el mismo nombre para el mismo jugador
     * (no se anaden sufijos): si existe un mundo viejo se asume que el caller lo borrara
     * antes de crear uno nuevo.
     */
    private String generateWorldName(Player owner) {
        String safe = owner.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (safe.isBlank()) safe = "p";
        if (safe.length() > 16) safe = safe.substring(0, 16);
        String uuid8 = owner.getUniqueId().toString().replace("-", "").substring(0, 8);
        return WORLD_PREFIX + safe + "_" + uuid8;
    }

    /**
     * Borra el pocketworld del jugador (mundo + entrada).
     * En Folia, si el mundo no puede ser descargado en runtime, se MARCA para borrar
     * en el proximo arranque y se "olvida" del registro de ETCWorlds (asi el siguiente
     * /pw create generara uno nuevo con nombre distinto sin colisionar).
     */
    public boolean delete(UUID owner) {
        PocketWorld pw = byOwner.get(owner);
        if (pw == null) return false;
        boolean ok = false;
        try {
            ok = plugin.worlds().deleteWorld(pw.worldName);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudo borrar pocketworld " + pw.worldName, ex);
        }
        if (!ok) {
            // Folia / archivos bloqueados: olvidar del registro y dejarlo pendiente.
            plugin.worlds().forgetWorld(pw.worldName);
            markPendingDelete(pw.worldName);
            plugin.getLogger().warning("[PocketWorld] " + pw.worldName
                    + " marcado para eliminar al reiniciar (Folia no permite unload en runtime).");
        }
        // En cualquier caso, sacar del estado in-memory para que el dueno pueda crear otro.
        byOwner.remove(owner);
        byWorldName.remove(pw.worldName.toLowerCase());
        save();
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
