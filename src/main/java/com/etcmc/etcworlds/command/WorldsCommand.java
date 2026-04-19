package com.etcmc.etcworlds.command;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.CustomPortal;
import com.etcmc.etcworlds.model.WorldRules;
import com.etcmc.etcworlds.model.WorldTemplate;
import com.etcmc.etcworlds.util.WorldFiles;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * /etcworlds &lt;subcomando&gt; — administración general.
 *
 * Subcomandos:
 *   create &lt;name&gt; [template] [environment] [seed]   — crea un mundo
 *   delete &lt;name&gt;                                   — elimina (destructivo)
 *   load &lt;name&gt;                                      — carga
 *   unload &lt;name&gt; [save|nosave]                      — descarga
 *   import &lt;folder&gt; [name]                           — registra mundo existente
 *   importzip &lt;zip&gt; &lt;name&gt;                          — importa desde zip
 *   export &lt;name&gt;                                     — exporta a zip
 *   backup &lt;name&gt;                                     — snapshot manual
 *   set &lt;world&gt; &lt;property&gt; &lt;value&gt;                  — cambia regla
 *   link &lt;world&gt; &lt;nether|end&gt; &lt;target&gt;              — vincula dimensión
 *   spawn &lt;world&gt;                                     — define spawn (donde estás)
 *   reload                                            — recarga config
 *   list / info [world]                               — info
 *   tp [player] &lt;world&gt;                               — teletransporte
 *   templates                                         — lista templates disponibles
 */
public class WorldsCommand implements CommandExecutor, TabCompleter {

    private final ETCWorlds plugin;

    public WorldsCommand(ETCWorlds plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        if (a.length == 0) { help(s); return true; }
        String sub = a[0].toLowerCase(Locale.ROOT);
        try {
            return switch (sub) {
                case "help", "?" -> { help(s); yield true; }
                case "create" -> create(s, a);
                case "delete", "remove" -> delete(s, a);
                case "load" -> load(s, a);
                case "unload" -> unload(s, a);
                case "import" -> importExisting(s, a);
                case "importzip" -> importZip(s, a);
                case "export" -> export(s, a);
                case "backup" -> backup(s, a);
                case "set" -> set(s, a);
                case "link" -> link(s, a);
                case "setspawn", "spawn" -> setSpawn(s, a);
                case "setlobby" -> setLobby(s, a);
                case "reload" -> reload(s);
                case "list" -> { list(s); yield true; }
                case "info" -> info(s, a);
                case "tp" -> tp(s, a);
                case "templates" -> { templates(s); yield true; }
                case "gui", "menu" -> guiOpen(s);
                case "clone" -> clone(s, a);
                case "instance" -> instance(s, a);
                case "pregen", "pregenerate" -> pregen(s, a);
                case "seeds" -> { seeds(s); yield true; }
                case "status" -> { status(s); yield true; }
                case "gamerule", "gr" -> gamerule(s, a);
                case "portal" -> portal(s, a);
                case "tpall", "teleportall" -> tpall(s, a);
                case "seed" -> seedShow(s, a);
                case "weather" -> weather(s, a);
                case "time" -> time(s, a);
                case "pvp" -> pvp(s, a);
                case "difficulty", "diff" -> difficulty(s, a);
                case "save" -> save(s, a);
                case "fly" -> fly(s, a);
                case "motd" -> motd(s, a);
                case "pw", "pocketworld" -> pocketworld(s, a);
                default -> { help(s); yield true; }
            };
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            s.sendMessage(ChatColor.RED + "Error: " + msg);
            plugin.getLogger().warning(ex.toString());
            return true;
        }
    }

    // -------------------------------------------------------------------------------------------
    //   SUBCOMANDOS
    // -------------------------------------------------------------------------------------------

    private boolean create(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.create")) { noPerm(s); return true; }
        if (a.length < 2) { s.sendMessage(ChatColor.YELLOW + "Uso: /etcworlds create <name> [template] [env] [seed] [opt=val ...]"); return true; }
        String name = a[1];
        WorldRules r = new WorldRules();
        r.name = name;
        if (a.length >= 3) r.template = WorldTemplate.fromString(a[2]);
        if (a.length >= 4) {
            try { r.environment = World.Environment.valueOf(a[3].toUpperCase()); }
            catch (IllegalArgumentException e) { s.sendMessage(ChatColor.RED + "Environment inválido."); return true; }
            r.ambient = r.environment;
        }
        if (a.length >= 5) {
            try { r.seed = Long.parseLong(a[4]); }
            catch (NumberFormatException e) { r.seed = a[4].hashCode(); }
        }
        // Opciones extra: ambient=NETHER biome=desert miny=-256 maxy=512 generator=stone:64;glass:80
        for (int i = 5; i < a.length; i++) {
            String[] kv = a[i].split("=", 2);
            if (kv.length != 2) continue;
            applyCreateOption(r, kv[0], kv[1]);
        }
        // Validar combinación template + environment
        if (r.environment != World.Environment.NORMAL &&
                (r.template == WorldTemplate.FLAT ||
                 r.template == WorldTemplate.AMPLIFIED ||
                 r.template == WorldTemplate.LARGE_BIOMES)) {
            s.sendMessage(ChatColor.RED + "Template " + r.template + " solo es compatible con environment NORMAL.");
            s.sendMessage(ChatColor.GRAY + "Para entornos Nether/End usa template NORMAL o VOID.");
            return true;
        }
        final WorldRules rules = r;
        s.sendMessage(ChatColor.GRAY + "Creando mundo " + ChatColor.WHITE + name + ChatColor.GRAY +
                " (" + rules.template + " / " + rules.environment + ")...");
        // createWorld debe ejecutarse en el hilo global (Folia requirement)
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                long t0 = System.currentTimeMillis();
                World w = plugin.worlds().createWorld(name, rules);
                if (w == null) s.sendMessage(ChatColor.RED + "Falló la creación del mundo.");
                else s.sendMessage(ChatColor.GREEN + "Mundo " + name + " creado en " + (System.currentTimeMillis() - t0) + "ms.");
            } catch (Exception ex) {
                String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                s.sendMessage(ChatColor.RED + "Error creando mundo: " + msg);
                plugin.getLogger().warning("create world error: " + ex);
            }
        });
        return true;
    }

    private void applyCreateOption(WorldRules r, String key, String val) {
        switch (key.toLowerCase(Locale.ROOT)) {
            case "ambient" -> { try { r.ambient = World.Environment.valueOf(val.toUpperCase()); } catch (Exception ignored) {} }
            case "biome" -> r.biomeForce = val;
            case "miny" -> { try { r.customMinY = Integer.parseInt(val); } catch (Exception ignored) {} }
            case "maxy" -> { try { r.customMaxY = Integer.parseInt(val); } catch (Exception ignored) {} }
            case "generator", "gen" -> r.generatorId = val;
            case "structures" -> r.generateStructures = Boolean.parseBoolean(val);
            case "group" -> r.worldGroup = val;
            case "template-mark" -> r.isTemplate = Boolean.parseBoolean(val);
            case "instance" -> r.perPlayerInstance = Boolean.parseBoolean(val);
            case "keep-loaded" -> r.keepLoaded = Boolean.parseBoolean(val);
        }
    }

    private boolean delete(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.delete")) { noPerm(s); return true; }
        if (a.length < 2) { s.sendMessage(ChatColor.YELLOW + "Uso: /etcworlds delete <name> confirm"); return true; }
        if (a.length < 3 || !a[2].equalsIgnoreCase("confirm")) {
            s.sendMessage(ChatColor.RED + "ATENCIÓN: esto borra todos los archivos del mundo.");
            s.sendMessage(ChatColor.YELLOW + "Confirma: " + ChatColor.WHITE + "/etcworlds delete " + a[1] + " confirm");
            return true;
        }
        String toDelete = a[1];
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            if (plugin.worlds().deleteWorld(toDelete)) s.sendMessage(ChatColor.GREEN + "Mundo eliminado: " + toDelete);
            else s.sendMessage(ChatColor.RED + "No se pudo eliminar.");
        });
        return true;
    }

    private boolean load(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.load")) { noPerm(s); return true; }
        if (a.length < 2) return true;
        String name = a[1];
        s.sendMessage(ChatColor.GRAY + "Cargando " + name + "...");
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                World w = plugin.worlds().loadWorld(name);
                s.sendMessage(w != null ? ChatColor.GREEN + "Cargado: " + name : ChatColor.RED + "No se pudo cargar: " + name);
            } catch (Exception ex) {
                s.sendMessage(ChatColor.RED + "Error: " + ex.getMessage());
            }
        });
        return true;
    }

    private boolean unload(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.unload")) { noPerm(s); return true; }
        if (a.length < 2) return true;
        String name = a[1];
        boolean save = a.length < 3 || !a[2].equalsIgnoreCase("nosave");
        World w = Bukkit.getWorld(name);
        if (w != null && !w.getPlayers().isEmpty()) {
            s.sendMessage(ChatColor.RED + "No se puede descargar: hay jugadores en el mundo.");
            return true;
        }
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            if (plugin.worlds().unloadWorld(name, save))
                s.sendMessage(ChatColor.GREEN + "Descargado: " + name);
            else
                s.sendMessage(ChatColor.RED + "No se pudo descargar.");
        });
        return true;
    }

    private boolean importExisting(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.import")) { noPerm(s); return true; }
        if (a.length < 2) { s.sendMessage(ChatColor.YELLOW + "Uso: /etcworlds import <folder> [name]"); return true; }
        String folder = a[1];
        String name = a.length >= 3 ? a[2] : new File(folder).getName();
        if (plugin.worlds().importExisting(name, folder))
            s.sendMessage(ChatColor.GREEN + "Importado: " + name + " (carpeta: " + folder + ")");
        else
            s.sendMessage(ChatColor.RED + "No se pudo importar (¿existe el level.dat?).");
        return true;
    }

    private boolean importZip(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.import")) { noPerm(s); return true; }
        if (a.length < 3) { s.sendMessage(ChatColor.YELLOW + "Uso: /etcworlds importzip <zip> <name>"); return true; }
        File zip = new File(a[1]);
        if (!zip.exists()) { s.sendMessage(ChatColor.RED + "Zip no encontrado."); return true; }
        String name = a[2];
        String dirName = plugin.getConfig().getString("worlds-folder", "mundos");
        File target = new File(Bukkit.getWorldContainer(), dirName + "/" + name);
        if (target.exists()) { s.sendMessage(ChatColor.RED + "Carpeta destino ya existe."); return true; }
        try {
            WorldFiles.unzip(zip, target.getParentFile());
            // Si el zip tenía una carpeta con otro nombre, renombrar
            File extracted = new File(target.getParentFile(), zip.getName().replaceAll("\\.zip$", ""));
            if (!target.exists() && extracted.exists()) extracted.renameTo(target);
            if (plugin.worlds().importExisting(name, dirName + "/" + name))
                s.sendMessage(ChatColor.GREEN + "Importado desde zip: " + name);
            else
                s.sendMessage(ChatColor.RED + "Zip extraído pero no contiene level.dat válido.");
        } catch (Exception ex) { s.sendMessage(ChatColor.RED + "Error: " + ex.getMessage()); }
        return true;
    }

    private boolean export(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.export")) { noPerm(s); return true; }
        if (a.length < 2) return true;
        File dir = plugin.worlds().worldDirOf(a[1]);
        if (dir == null || !dir.exists()) { s.sendMessage(ChatColor.RED + "Mundo no registrado."); return true; }
        s.sendMessage(ChatColor.GRAY + "Exportando " + a[1] + "... (puede tardar)");
        Bukkit.getAsyncScheduler().runNow(plugin, t -> {
            try {
                File out = new File(plugin.getDataFolder(), "exports/" + a[1] + ".zip");
                WorldFiles.zip(dir, out);
                s.sendMessage(ChatColor.GREEN + "Exportado: " + out.getAbsolutePath() +
                        " (" + WorldFiles.formatSize(out.length()) + ")");
            } catch (Exception ex) { s.sendMessage(ChatColor.RED + "Error: " + ex.getMessage()); }
        });
        return true;
    }

    private boolean backup(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.backup")) { noPerm(s); return true; }
        if (a.length < 2) return true;
        s.sendMessage(ChatColor.GRAY + "Backup de " + a[1] + " iniciado...");
        Bukkit.getAsyncScheduler().runNow(plugin, t -> {
            try {
                File f = plugin.backups().backupWorld(a[1]);
                s.sendMessage(ChatColor.GREEN + "Backup OK: " + f.getName());
            } catch (Exception ex) { s.sendMessage(ChatColor.RED + "Error: " + ex.getMessage()); }
        });
        return true;
    }

    private boolean set(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.set")) { noPerm(s); return true; }
        if (a.length < 4) {
            s.sendMessage(ChatColor.YELLOW + "Uso: /etcworlds set <world> <property> <value>");
            s.sendMessage(ChatColor.GRAY + "Propiedades: pvp, mob-spawn, animal-spawn, fall-damage, hunger, " +
                    "build, fly, gamemode, keep-inv, weather-locked, time-locked, fixed-time, " +
                    "border-size, view-distance, public-access, access-permission, group, keep-loaded, template, instance");
            return true;
        }
        String world = a[1];
        WorldRules r = plugin.worlds().getRules(world);
        if (r == null) { s.sendMessage(ChatColor.RED + "Mundo no registrado."); return true; }
        String prop = a[2].toLowerCase(Locale.ROOT);
        String val = a[3];
        switch (prop) {
            case "pvp" -> r.pvp = Boolean.parseBoolean(val);
            case "mob-spawn" -> r.mobSpawn = Boolean.parseBoolean(val);
            case "animal-spawn" -> r.animalSpawn = Boolean.parseBoolean(val);
            case "fall-damage" -> r.fallDamage = Boolean.parseBoolean(val);
            case "hunger" -> r.hunger = Boolean.parseBoolean(val);
            case "build" -> r.buildEnabled = Boolean.parseBoolean(val);
            case "fly" -> r.fly = Boolean.parseBoolean(val);
            case "gamemode" -> r.gamemode = "FREE".equalsIgnoreCase(val) ? null
                    : safeEnum(GameMode.class, val, r.gamemode);
            case "keep-inv" -> r.keepInventoryOnDeath = Boolean.parseBoolean(val);
            case "weather-locked" -> r.weatherLocked = Boolean.parseBoolean(val);
            case "time-locked" -> r.timeLocked = Boolean.parseBoolean(val);
            case "fixed-time" -> r.fixedTimeTick = Long.parseLong(val);
            case "border-size" -> r.borderSize = Double.parseDouble(val);
            case "view-distance" -> r.viewDistance = Integer.parseInt(val);
            case "public-access" -> r.publicAccess = Boolean.parseBoolean(val);
            case "access-permission" -> r.accessPermission = val;
            case "group" -> r.worldGroup = val;
            case "keep-loaded" -> r.keepLoaded = Boolean.parseBoolean(val);
            case "template" -> r.isTemplate = Boolean.parseBoolean(val);
            case "instance" -> r.perPlayerInstance = Boolean.parseBoolean(val);
            case "biome" -> r.biomeForce = val;
            case "enter-message" -> r.enterMessage = val.replace("_", " ");
            case "exit-message"  -> r.exitMessage  = val.replace("_", " ");
            default -> { s.sendMessage(ChatColor.RED + "Propiedad desconocida."); return true; }
        }
        plugin.worlds().saveRules(world);
        World w = Bukkit.getWorld(world);
        if (w != null) plugin.worlds().applyRules(w, r);
        s.sendMessage(ChatColor.GREEN + prop + " = " + val);
        return true;
    }

    private boolean link(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.set")) { noPerm(s); return true; }
        if (a.length < 4) { s.sendMessage(ChatColor.YELLOW + "Uso: /etcworlds link <world> <nether|end> <target|none>"); return true; }
        WorldRules r = plugin.worlds().getRules(a[1]);
        if (r == null) { s.sendMessage(ChatColor.RED + "Mundo no registrado."); return true; }
        String dim = a[2].toLowerCase(Locale.ROOT);
        String target = "none".equalsIgnoreCase(a[3]) ? "" : a[3];
        if ("nether".equals(dim)) r.linkedNether = target;
        else if ("end".equals(dim)) r.linkedEnd = target;
        else { s.sendMessage(ChatColor.RED + "Dimensión inválida."); return true; }
        plugin.worlds().saveRules(a[1]);
        s.sendMessage(ChatColor.GREEN + "Link " + dim + " de " + a[1] + " → " + (target.isEmpty() ? "(ninguno)" : target));
        return true;
    }

    private boolean setSpawn(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.spawn.set")) { noPerm(s); return true; }
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        String name = a.length >= 2 ? a[1] : p.getWorld().getName();
        WorldRules r = plugin.worlds().getRules(name);
        if (r == null) { s.sendMessage(ChatColor.RED + "Mundo no registrado."); return true; }
        Location loc = p.getLocation();
        r.spawnX = loc.getX(); r.spawnY = loc.getY(); r.spawnZ = loc.getZ();
        r.spawnYaw = loc.getYaw(); r.spawnPitch = loc.getPitch();
        plugin.worlds().saveRules(name);
        World w = Bukkit.getWorld(name);
        if (w != null) w.setSpawnLocation(loc);
        s.sendMessage(ChatColor.GREEN + "Spawn de " + name + " definido en " + fmt(loc) + ".");
        return true;
    }

    /** /ecw setlobby [world] - Guarda el mundo lobby en config.yml. */
    private boolean setLobby(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.spawn.set")) { noPerm(s); return true; }
        String name;
        if (a.length >= 2) name = a[1];
        else if (s instanceof Player p) name = p.getWorld().getName();
        else { s.sendMessage(ChatColor.RED + "Uso: /ecw setlobby <mundo>"); return true; }
        World w = Bukkit.getWorld(name);
        String resolved = plugin.worlds().resolveWorldName(name);
        if (w == null && resolved == null) {
            s.sendMessage(ChatColor.RED + "Mundo '" + name + "' no encontrado o no registrado.");
            return true;
        }
        String finalName = resolved != null ? resolved : name;
        plugin.getConfig().set("lobby-world", finalName);
        plugin.saveConfig();
        s.sendMessage(ChatColor.GREEN + "Lobby definido a '" + finalName + "'. /lobby ahora apunta aqui.");
        return true;
    }

    private boolean reload(CommandSender s) {
        if (!s.hasPermission("etcworlds.reload")) { noPerm(s); return true; }
        plugin.worlds().shutdown();
        plugin.worlds().loadRegistry();
        s.sendMessage(ChatColor.GREEN + "ETCWorlds recargado.");
        return true;
    }

    private void list(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "=== Mundos administrados ===");
        for (String name : plugin.worlds().getManagedNames()) {
            World w = Bukkit.getWorld(name);
            String state = w != null ? ChatColor.GREEN + "cargado(" + w.getPlayers().size() + ")" : ChatColor.GRAY + "descargado";
            WorldRules r = plugin.worlds().getRules(name);
            String tmpl = r != null ? r.template.name() : "?";
            s.sendMessage(ChatColor.YELLOW + name + " " + state + ChatColor.DARK_GRAY + " [" + tmpl + "]");
        }
        s.sendMessage(ChatColor.GRAY + "Total: " + plugin.worlds().getManagedNames().size());
    }

    private boolean info(CommandSender s, String[] a) {
        String name = a.length >= 2 ? a[1] : (s instanceof Player p ? p.getWorld().getName() : Bukkit.getWorlds().get(0).getName());
        WorldRules r = plugin.worlds().getRules(name);
        World w = Bukkit.getWorld(name);
        s.sendMessage(ChatColor.GOLD + "=== " + name + " ===");
        s.sendMessage(ChatColor.GRAY + "Cargado: " + (w != null));
        if (w != null) {
            s.sendMessage(ChatColor.GRAY + "Jugadores: " + ChatColor.WHITE + w.getPlayers().size());
            s.sendMessage(ChatColor.GRAY + "Env: " + w.getEnvironment() + "  Difficulty: " + w.getDifficulty());
            s.sendMessage(ChatColor.GRAY + "Spawn: " + fmt(w.getSpawnLocation()));
            s.sendMessage(ChatColor.GRAY + "Seed: " + w.getSeed());
            File dir = plugin.worlds().worldDirOf(name);
            if (dir != null) s.sendMessage(ChatColor.GRAY + "Tamaño en disco: " + WorldFiles.formatSize(WorldFiles.sizeOf(dir)));
        }
        if (r != null) {
            s.sendMessage(ChatColor.GRAY + "Template: " + r.template + "  Group: " + r.worldGroup);
            s.sendMessage(ChatColor.GRAY + "PvP: " + r.pvp + "  Build: " + r.buildEnabled + "  Fly: " + r.fly);
            s.sendMessage(ChatColor.GRAY + "Links: nether=" + (r.linkedNether.isEmpty() ? "-" : r.linkedNether)
                    + " end=" + (r.linkedEnd.isEmpty() ? "-" : r.linkedEnd));
            s.sendMessage(ChatColor.GRAY + "Acceso: public=" + r.publicAccess + " perm=" + (r.accessPermission.isEmpty() ? "-" : r.accessPermission));
        }
        return true;
    }

    private boolean tp(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.tp")) { noPerm(s); return true; }
        if (a.length < 2) return true;
        Player target;
        String world;
        if (a.length >= 3) {
            if (!s.hasPermission("etcworlds.tp.others")) { noPerm(s); return true; }
            target = Bukkit.getPlayer(a[1]);
            world = a[2];
        } else {
            if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Especifica jugador."); return true; }
            target = p;
            world = a[1];
        }
        if (target == null) { s.sendMessage(ChatColor.RED + "Jugador no encontrado."); return true; }
        plugin.lazyTeleport().teleport(target, world, null);
        return true;
    }

    private void templates(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "Templates: " + ChatColor.YELLOW +
                Arrays.stream(WorldTemplate.values()).map(Enum::name).collect(Collectors.joining(", ")));
    }

    private boolean guiOpen(CommandSender s) {
        if (!s.hasPermission("etcworlds.gui")) { noPerm(s); return true; }
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        plugin.gui().open(p);
        return true;
    }

    private boolean clone(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.template")) { noPerm(s); return true; }
        if (a.length < 3) { s.sendMessage(ChatColor.YELLOW + "Uso: /etcworlds clone <plantilla> <nuevoNombre>"); return true; }
        s.sendMessage(ChatColor.GRAY + "Clonando " + a[1] + " -> " + a[2] + "...");
        Bukkit.getAsyncScheduler().runNow(plugin, t -> {
            try {
                if (plugin.templates().cloneTemplate(a[1], a[2]))
                    s.sendMessage(ChatColor.GREEN + "Clon completado: " + a[2]);
            } catch (Exception ex) { s.sendMessage(ChatColor.RED + "Error: " + ex.getMessage()); }
        });
        return true;
    }

    private boolean instance(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.tp")) { noPerm(s); return true; }
        if (a.length < 2) { s.sendMessage(ChatColor.YELLOW + "Uso: /etcworlds instance <plantilla>"); return true; }
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        String template = a[1];
        s.sendMessage(ChatColor.GRAY + "Preparando instancia de " + template + "...");
        // ensureInstance puede llamar loadWorld — debe correr en el hilo global
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                World w = plugin.instances().ensureInstance(p, template);
                plugin.lazyTeleport().teleport(p, w.getName(), null);
            } catch (Exception ex) {
                String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                p.sendMessage(ChatColor.RED + "Error: " + msg);
                plugin.getLogger().warning("instance error: " + ex);
            }
        });
        return true;
    }

    private boolean pregen(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.set")) { noPerm(s); return true; }
        if (a.length < 3) { s.sendMessage(ChatColor.YELLOW + "Uso: /etcworlds pregen <world> <radioChunks>"); return true; }
        if (!plugin.regionGen().isEnabled()) {
            s.sendMessage(ChatColor.RED + "ETCRegionGenerator no está cargado.");
            return true;
        }
        try {
            int rad = Integer.parseInt(a[2]);
            plugin.regionGen().pregenerate(a[1], rad);
            s.sendMessage(ChatColor.GREEN + "Pre-gen lanzado para " + a[1] + " radio " + rad);
        } catch (NumberFormatException ex) { s.sendMessage(ChatColor.RED + "Radio inválido."); }
        return true;
    }

    private void seeds(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "Seed presets disponibles:");
        com.etcmc.etcworlds.util.SeedPresets.PRESETS.forEach((name, seed) ->
                s.sendMessage(ChatColor.YELLOW + "  " + name + " " + ChatColor.GRAY + "= " + seed));
        s.sendMessage(ChatColor.GRAY + "Usar: /etcworlds create <name> NORMAL NORMAL <seed>");
    }

    /** /ecw status — resumen de todos los mundos con RAM y estado. */
    private void status(CommandSender s) {
        if (!s.hasPermission("etcworlds.admin")) { noPerm(s); return; }
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576L;
        long totalMb = rt.totalMemory() / 1_048_576L;
        s.sendMessage(ChatColor.GOLD + "=== ETCWorlds Status  RAM: " + usedMb + "/" + totalMb + " MB ===");
        for (String name : plugin.worlds().getManagedNames()) {
            World w = Bukkit.getWorld(name);
            WorldRules r = plugin.worlds().getRules(name);
            String tmpl = r != null ? r.template.name() : "?";
            if (w != null) {
                int chunks  = w.getLoadedChunks().length;
                int players = w.getPlayers().size();
                s.sendMessage(ChatColor.GREEN + "● " + ChatColor.YELLOW + name
                        + ChatColor.GRAY + " [" + tmpl + "] chunks=" + chunks + " players=" + players);
            } else {
                s.sendMessage(ChatColor.DARK_GRAY + "○ " + name + ChatColor.GRAY + " [" + tmpl + "] (descargado)");
            }
        }
    }

    /** /ecw gamerule &lt;world&gt; &lt;rule&gt; &lt;value&gt; */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean gamerule(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.set")) { noPerm(s); return true; }
        if (a.length < 4) { s.sendMessage(ChatColor.YELLOW + "Uso: /ecw gamerule <world> <rule> <value>"); return true; }
        World w = Bukkit.getWorld(a[1]);
        if (w == null) { s.sendMessage(ChatColor.RED + "Mundo no cargado: " + a[1]); return true; }
        GameRule rule = GameRule.getByName(a[2]);
        if (rule == null) { s.sendMessage(ChatColor.RED + "GameRule desconocida: " + a[2]); return true; }
        Object val;
        if (rule.getType() == Boolean.class) val = Boolean.parseBoolean(a[3]);
        else {
            try { val = Integer.parseInt(a[3]); }
            catch (NumberFormatException ex) { s.sendMessage(ChatColor.RED + "Valor inválido."); return true; }
        }
        w.setGameRule(rule, val);
        s.sendMessage(ChatColor.GREEN + "GameRule " + a[2] + " = " + a[3] + " en " + a[1] + ".");
        return true;
    }

    /** /ecw portal create|delete|list|tp */
    private boolean portal(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.portal")) { noPerm(s); return true; }
        if (a.length < 2) {
            s.sendMessage(ChatColor.YELLOW + "Uso: /ecw portal create <name> | delete <name> | list | tp <name>");
            return true;
        }
        return switch (a[1].toLowerCase()) {
            case "create" -> portalCreate(s, a);
            case "delete", "remove" -> portalDelete(s, a);
            case "list" -> { portalList(s); yield true; }
            case "tp" -> portalTp(s, a);
            default -> { s.sendMessage(ChatColor.YELLOW + "Subcomandos: create, delete, list, tp"); yield true; }
        };
    }

    private boolean portalCreate(CommandSender s, String[] a) {
        // /ecw portal create <name> <destWorld> [x y z]
        if (a.length < 4) { s.sendMessage(ChatColor.YELLOW + "Uso: /ecw portal create <name> <destWorld> [x y z]"); return true; }
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        CustomPortal portal = new CustomPortal();
        portal.name = a[2];
        portal.destinationWorld = a[3];
        portal.triggerWorld = p.getWorld().getName();
        Location loc = p.getLocation();
        portal.triggerX = loc.getBlockX();
        portal.triggerY = loc.getBlockY();
        portal.triggerZ = loc.getBlockZ();
        if (a.length >= 7) {
            try {
                portal.destX = Double.parseDouble(a[4]);
                portal.destY = Double.parseDouble(a[5]);
                portal.destZ = Double.parseDouble(a[6]);
            } catch (NumberFormatException ex) { s.sendMessage(ChatColor.RED + "Coordenadas inválidas."); return true; }
        }
        plugin.portals().add(portal);
        s.sendMessage(ChatColor.GREEN + "Portal '" + portal.name + "' creado en " + portal.triggerWorld
                + " " + portal.triggerX + "," + portal.triggerY + "," + portal.triggerZ
                + " -> " + portal.destinationWorld);
        return true;
    }

    private boolean portalDelete(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage(ChatColor.YELLOW + "Uso: /ecw portal delete <name>"); return true; }
        if (plugin.portals().remove(a[2])) s.sendMessage(ChatColor.GREEN + "Portal '" + a[2] + "' eliminado.");
        else s.sendMessage(ChatColor.RED + "Portal no encontrado: " + a[2]);
        return true;
    }

    private void portalList(CommandSender s) {
        if (plugin.portals().count() == 0) { s.sendMessage(ChatColor.GRAY + "No hay portales registrados."); return; }
        s.sendMessage(ChatColor.GOLD + "Portales (" + plugin.portals().count() + "):");
        for (CustomPortal p : plugin.portals().all())
            s.sendMessage(ChatColor.YELLOW + "  " + p.name + ChatColor.GRAY
                    + " [" + p.triggerWorld + " " + p.triggerX + "," + p.triggerY + "," + p.triggerZ
                    + " -> " + p.destinationWorld + "]");
    }

    private boolean portalTp(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage(ChatColor.YELLOW + "Uso: /ecw portal tp <name>"); return true; }
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        CustomPortal portal = plugin.portals().get(a[2]);
        if (portal == null) { s.sendMessage(ChatColor.RED + "Portal no encontrado."); return true; }
        Location dest = portal.hasCustomDest()
                ? new Location(Bukkit.getWorld(portal.destinationWorld), portal.destX, portal.destY, portal.destZ,
                               portal.destYaw, portal.destPitch)
                : null;
        plugin.lazyTeleport().teleport(p, portal.destinationWorld, dest);
        return true;
    }

    /** /ecw tpall &lt;world&gt; — teleports all online players to a world. */
    private boolean tpall(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.tp.others")) { noPerm(s); return true; }
        if (a.length < 2) { s.sendMessage(ChatColor.YELLOW + "Uso: /ecw tpall <world>"); return true; }
        String worldName = a[1];
        if (plugin.worlds().getRules(worldName) == null) {
            s.sendMessage(ChatColor.RED + "Mundo no registrado: " + worldName);
            return true;
        }
        int count = 0;
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            plugin.lazyTeleport().teleport(p, worldName, null);
            count++;
        }
        s.sendMessage(ChatColor.GREEN + "" + count + " jugadores teletransportados a " + worldName + ".");
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "=== ETCWorlds ===");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds create <name> [tmpl] [env] [seed] [opt=val ...]");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds delete <name> confirm");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds load|unload <name>");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds import <folder> [name]");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds importzip <zip> <name>");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds export <name>");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds backup <name>");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds set <world> <prop> <val>");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds link <world> <nether|end> <target|none>");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds setspawn [world]   - Define spawn del mundo");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds setlobby [world]   - Define mundo lobby (config.yml)");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds tp [player] <world>");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds list / info / templates / reload");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds gui / clone / instance / pregen / seeds");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds status");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds gamerule <world> <rule> <value>");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds portal create|delete|list|tp");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds tpall <world>");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds seed [world]                       - Muestra la semilla");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds weather <world> <clear|rain|thunder> [seg]");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds time <world> <day|night|noon|midnight|tick>");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds pvp <world> <on|off>");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds difficulty <world> <peaceful|easy|normal|hard>");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds save <world>                       - Fuerza world.save()");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds fly <world> <on|off>               - Fly automatico al entrar");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds motd <world> <texto>               - Mensaje al entrar");
        s.sendMessage(ChatColor.YELLOW + "/etcworlds pw [sub]                           - PocketWorlds (alias /pw)");
    }

    private void noPerm(CommandSender s) {
        s.sendMessage(ChatColor.RED + plugin.getConfig().getString("messages.no-permission", "Sin permiso."));
    }

    private String fmt(Location l) {
        return l.getWorld().getName() + " " + (int) l.getX() + " " + (int) l.getY() + " " + (int) l.getZ();
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E safeEnum(Class<E> e, String v, E def) {
        try { return Enum.valueOf(e, v.toUpperCase(Locale.ROOT)); } catch (Exception ex) { return def; }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        if (a.length == 1) return List.of("create","delete","load","unload","import","importzip","export","backup",
                "set","link","setspawn","setlobby","reload","list","info","tp","templates","gui","clone","instance","pregen","seeds",
                "status","gamerule","portal","tpall","seed","weather","time","pvp","difficulty","save","fly","motd","pw","help").stream()
                .filter(x -> x.startsWith(a[0].toLowerCase())).toList();
        if (a.length == 2 && (a[0].equalsIgnoreCase("delete") || a[0].equalsIgnoreCase("load")
                || a[0].equalsIgnoreCase("unload") || a[0].equalsIgnoreCase("export")
                || a[0].equalsIgnoreCase("backup") || a[0].equalsIgnoreCase("info")
                || a[0].equalsIgnoreCase("setspawn") || a[0].equalsIgnoreCase("spawn")
                || a[0].equalsIgnoreCase("setlobby") || a[0].equalsIgnoreCase("set")
                || a[0].equalsIgnoreCase("link") || a[0].equalsIgnoreCase("tp")))
            return new ArrayList<>(plugin.worlds().getManagedNames());
        if (a.length == 3 && a[0].equalsIgnoreCase("create"))
            return Arrays.stream(WorldTemplate.values()).map(Enum::name).toList();
        if (a.length == 4 && a[0].equalsIgnoreCase("create"))
            return Arrays.stream(World.Environment.values()).map(Enum::name).toList();
        if (a.length == 3 && a[0].equalsIgnoreCase("link"))
            return List.of("nether", "end");
        if (a.length == 2 && (a[0].equalsIgnoreCase("seed") || a[0].equalsIgnoreCase("weather")
                || a[0].equalsIgnoreCase("time") || a[0].equalsIgnoreCase("pvp")
                || a[0].equalsIgnoreCase("difficulty") || a[0].equalsIgnoreCase("diff")
                || a[0].equalsIgnoreCase("save") || a[0].equalsIgnoreCase("fly")
                || a[0].equalsIgnoreCase("motd")))
            return new ArrayList<>(plugin.worlds().getManagedNames());
        if (a.length == 3 && a[0].equalsIgnoreCase("weather"))
            return List.of("clear", "rain", "thunder");
        if (a.length == 3 && a[0].equalsIgnoreCase("time"))
            return List.of("day", "night", "noon", "midnight");
        if (a.length == 3 && (a[0].equalsIgnoreCase("pvp") || a[0].equalsIgnoreCase("fly")))
            return List.of("on", "off");
        if (a.length == 3 && (a[0].equalsIgnoreCase("difficulty") || a[0].equalsIgnoreCase("diff")))
            return List.of("peaceful", "easy", "normal", "hard");
        return List.of();
    }

    // -------------------------------------------------------------------------------------------
    //   NUEVOS SUBCOMANDOS UTILITARIOS
    // -------------------------------------------------------------------------------------------

    /** /ecw seed [world] - Muestra la semilla del mundo. */
    private boolean seedShow(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.info")) { noPerm(s); return true; }
        String name;
        if (a.length >= 2) name = a[1];
        else if (s instanceof Player p) name = p.getWorld().getName();
        else { s.sendMessage(ChatColor.YELLOW + "Uso: /ecw seed <world>"); return true; }
        World w = Bukkit.getWorld(name);
        if (w == null) { s.sendMessage(ChatColor.RED + "Mundo no cargado: " + name); return true; }
        s.sendMessage(ChatColor.GOLD + "Seed de " + ChatColor.YELLOW + w.getName() + ChatColor.GOLD + ": "
                + ChatColor.WHITE + w.getSeed());
        return true;
    }

    /** /ecw weather <world> <clear|rain|thunder> [duration-seconds] */
    private boolean weather(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.set")) { noPerm(s); return true; }
        if (a.length < 3) {
            s.sendMessage(ChatColor.YELLOW + "Uso: /ecw weather <world> <clear|rain|thunder> [seg]");
            return true;
        }
        World w = Bukkit.getWorld(a[1]);
        if (w == null) { s.sendMessage(ChatColor.RED + "Mundo no cargado: " + a[1]); return true; }
        int durationTicks = a.length >= 4 ? safeInt(a[3], 600) * 20 : 6000;
        switch (a[2].toLowerCase(Locale.ROOT)) {
            case "clear", "sun" -> {
                w.setStorm(false); w.setThundering(false);
                w.setClearWeatherDuration(durationTicks);
            }
            case "rain" -> {
                w.setStorm(true); w.setThundering(false);
                w.setWeatherDuration(durationTicks);
            }
            case "thunder", "storm" -> {
                w.setStorm(true); w.setThundering(true);
                w.setWeatherDuration(durationTicks);
                w.setThunderDuration(durationTicks);
            }
            default -> { s.sendMessage(ChatColor.RED + "Tipo invalido. Usa clear|rain|thunder."); return true; }
        }
        s.sendMessage(ChatColor.GREEN + "Clima de " + a[1] + " cambiado a " + a[2] + ".");
        return true;
    }

    /** /ecw time <world> <day|night|noon|midnight|tick> */
    private boolean time(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.set")) { noPerm(s); return true; }
        if (a.length < 3) {
            s.sendMessage(ChatColor.YELLOW + "Uso: /ecw time <world> <day|night|noon|midnight|tick>");
            return true;
        }
        World w = Bukkit.getWorld(a[1]);
        if (w == null) { s.sendMessage(ChatColor.RED + "Mundo no cargado: " + a[1]); return true; }
        long t;
        switch (a[2].toLowerCase(Locale.ROOT)) {
            case "day" -> t = 1000;
            case "noon" -> t = 6000;
            case "night" -> t = 13000;
            case "midnight" -> t = 18000;
            default -> {
                try { t = Long.parseLong(a[2]); }
                catch (NumberFormatException ex) { s.sendMessage(ChatColor.RED + "Valor invalido."); return true; }
            }
        }
        w.setTime(t);
        s.sendMessage(ChatColor.GREEN + "Hora de " + a[1] + " = " + t + ".");
        return true;
    }

    /** /ecw pvp <world> <on|off> - atajo de /ecw set <w> pvp <bool> */
    private boolean pvp(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.set")) { noPerm(s); return true; }
        if (a.length < 3) { s.sendMessage(ChatColor.YELLOW + "Uso: /ecw pvp <world> <on|off>"); return true; }
        WorldRules r = plugin.worlds().getRules(a[1]);
        if (r == null) { s.sendMessage(ChatColor.RED + "Mundo no registrado."); return true; }
        boolean on = a[2].equalsIgnoreCase("on") || a[2].equalsIgnoreCase("true");
        r.pvp = on;
        plugin.worlds().saveRules(a[1]);
        World w = Bukkit.getWorld(a[1]);
        if (w != null) w.setPVP(on);
        s.sendMessage(ChatColor.GREEN + "PvP en " + a[1] + " = " + (on ? "ON" : "OFF"));
        return true;
    }

    /** /ecw difficulty <world> <peaceful|easy|normal|hard> */
    private boolean difficulty(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.set")) { noPerm(s); return true; }
        if (a.length < 3) { s.sendMessage(ChatColor.YELLOW + "Uso: /ecw difficulty <world> <peaceful|easy|normal|hard>"); return true; }
        World w = Bukkit.getWorld(a[1]);
        if (w == null) { s.sendMessage(ChatColor.RED + "Mundo no cargado: " + a[1]); return true; }
        try {
            org.bukkit.Difficulty d = org.bukkit.Difficulty.valueOf(a[2].toUpperCase(Locale.ROOT));
            w.setDifficulty(d);
            s.sendMessage(ChatColor.GREEN + "Dificultad de " + a[1] + " = " + d + ".");
        } catch (IllegalArgumentException ex) {
            s.sendMessage(ChatColor.RED + "Dificultad invalida.");
        }
        return true;
    }

    /** /ecw save <world> - fuerza world.save() en el hilo correcto. */
    private boolean save(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.admin")) { noPerm(s); return true; }
        if (a.length < 2) { s.sendMessage(ChatColor.YELLOW + "Uso: /ecw save <world>"); return true; }
        World w = Bukkit.getWorld(a[1]);
        if (w == null) { s.sendMessage(ChatColor.RED + "Mundo no cargado: " + a[1]); return true; }
        s.sendMessage(ChatColor.GRAY + "Guardando " + a[1] + "...");
        Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
            try { w.save(); s.sendMessage(ChatColor.GREEN + "Guardado: " + a[1]); }
            catch (Exception ex) { s.sendMessage(ChatColor.RED + "Error: " + ex.getMessage()); }
        });
        return true;
    }

    /** /ecw fly <world> <on|off> - atajo del campo fly. */
    private boolean fly(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.set")) { noPerm(s); return true; }
        if (a.length < 3) { s.sendMessage(ChatColor.YELLOW + "Uso: /ecw fly <world> <on|off>"); return true; }
        WorldRules r = plugin.worlds().getRules(a[1]);
        if (r == null) { s.sendMessage(ChatColor.RED + "Mundo no registrado."); return true; }
        boolean on = a[2].equalsIgnoreCase("on") || a[2].equalsIgnoreCase("true");
        r.fly = on;
        plugin.worlds().saveRules(a[1]);
        s.sendMessage(ChatColor.GREEN + "Fly automatico en " + a[1] + " = " + (on ? "ON" : "OFF"));
        return true;
    }

    /** /ecw motd <world> <texto...> - alias amigable de enter-message. */
    private boolean motd(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.set")) { noPerm(s); return true; }
        if (a.length < 3) { s.sendMessage(ChatColor.YELLOW + "Uso: /ecw motd <world> <texto...>"); return true; }
        WorldRules r = plugin.worlds().getRules(a[1]);
        if (r == null) { s.sendMessage(ChatColor.RED + "Mundo no registrado."); return true; }
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < a.length; i++) { if (i > 2) sb.append(' '); sb.append(a[i]); }
        r.enterMessage = sb.toString();
        plugin.worlds().saveRules(a[1]);
        s.sendMessage(ChatColor.GREEN + "MOTD de " + a[1] + " = " + ChatColor.translateAlternateColorCodes('&', r.enterMessage));
        return true;
    }

    /** /ecw pw [sub...] - delega al PocketWorldCommand. */
    private boolean pocketworld(CommandSender s, String[] a) {
        // Reempaqueta args quitando "pw"/"pocketworld" inicial
        String[] sub = a.length <= 1 ? new String[0] : Arrays.copyOfRange(a, 1, a.length);
        // Construimos un delegate liviano: invocamos directamente el PocketWorldCommand
        PocketWorldCommand pwc = new PocketWorldCommand(plugin);
        return pwc.onCommand(s, null, "pw", sub);
    }

    private static int safeInt(String v, int def) {
        try { return Integer.parseInt(v); } catch (Exception ex) { return def; }
    }
}
