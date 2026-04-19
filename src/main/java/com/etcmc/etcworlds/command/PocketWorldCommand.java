package com.etcmc.etcworlds.command;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.manager.PocketWorldManager;
import com.etcmc.etcworlds.manager.PocketWorldManager.PocketWorld;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /pw [sub] — gestión de PocketWorlds personales.
 *
 * Sin subcomando: teleporta al pocketworld del jugador (creándolo si no existe).
 *
 * Subcomandos:
 *   create                       crea tu pocketworld (si no existe)
 *   tp                           teleporta al tuyo
 *   home                         alias de tp
 *   reset confirm                borra y vuelve a crear el tuyo
 *   delete confirm               solo borra (sin recrear)
 *   list                         lista jugadores con pocketworld
 *   info                         info de tu pocketworld
 *   invite &lt;jugador&gt;         da acceso a otro jugador
 *   kick &lt;jugador&gt;           quita acceso
 *   visit &lt;jugador&gt;          va al pocketworld de otro (requiere invitación o etcworlds.pw.bypass)
 *   setspawn                     define el spawn dentro de tu pocketworld
 *   admin delete &lt;jugador&gt;   admin: borra el de otro (etcworlds.pw.admin)
 *   help                         ayuda
 */
public class PocketWorldCommand implements CommandExecutor, TabCompleter {

    private final ETCWorlds plugin;

    public PocketWorldCommand(ETCWorlds plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        if (!plugin.getConfig().getBoolean("pocketworlds.enabled", true)) {
            s.sendMessage(ChatColor.RED + "Los PocketWorlds están deshabilitados en este servidor.");
            return true;
        }
        // /pw  sin args  →  tp al propio
        if (a.length == 0) return tp(s);
        String sub = a[0].toLowerCase(Locale.ROOT);
        try {
            return switch (sub) {
                case "help", "?" -> { help(s); yield true; }
                case "tp", "home", "go" -> tp(s);
                case "create", "new" -> create(s);
                case "reset" -> reset(s, a);
                case "delete" -> deleteSelfCmd(s, a);
                case "list" -> { list(s); yield true; }
                case "info" -> info(s);
                case "invite" -> invite(s, a);
                case "kick", "uninvite", "remove-invite" -> kick(s, a);
                case "useradd" -> userAdd(s, a);
                case "userremove", "userdel", "userrem" -> userRemove(s, a);
                case "visit", "join" -> visit(s, a);
                case "setspawn" -> setSpawn(s);
                case "rules", "flags" -> rules(s, a);
                case "admin" -> admin(s, a);
                default -> { help(s); yield true; }
            };
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            s.sendMessage(ChatColor.RED + "Error: " + msg);
            plugin.getLogger().warning("PocketWorld error: " + ex);
            return true;
        }
    }

    // ===========================================================================================
    //   SUBCOMANDOS
    // ===========================================================================================

    /** /pw  o  /pw tp — teletransporta al propio pocketworld (lo crea si no existe). */
    private boolean tp(CommandSender s) {
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        if (!p.hasPermission("etcworlds.pw")) { noPerm(s); return true; }

        PocketWorldManager mgr = plugin.pocketWorlds();
        PocketWorld existing = mgr.get(p.getUniqueId());
        if (existing != null) {
            plugin.lazyTeleport().teleport(p, existing.worldName, null);
            return true;
        }
        if (!p.hasPermission("etcworlds.pw.create")) {
            s.sendMessage(ChatColor.RED + "No tienes permiso para crear un PocketWorld.");
            return true;
        }
        s.sendMessage(ChatColor.GRAY + "Creando tu PocketWorld por primera vez...");
        mgr.create(p, world -> {
            if (world == null) p.sendMessage(ChatColor.RED + "No se pudo crear tu PocketWorld.");
            else {
                p.sendMessage(ChatColor.GREEN + "PocketWorld creado: " + ChatColor.WHITE + world.getName());
                plugin.lazyTeleport().teleport(p, world.getName(), null);
            }
        });
        return true;
    }

    private boolean create(CommandSender s) {
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        if (!p.hasPermission("etcworlds.pw.create")) { noPerm(s); return true; }
        if (plugin.pocketWorlds().exists(p.getUniqueId())) {
            s.sendMessage(ChatColor.YELLOW + "Ya tienes un PocketWorld. Usa /pw tp para ir, o /pw reset confirm para regenerarlo.");
            return true;
        }
        s.sendMessage(ChatColor.GRAY + "Creando tu PocketWorld...");
        plugin.pocketWorlds().create(p, world -> {
            if (world == null) p.sendMessage(ChatColor.RED + "No se pudo crear tu PocketWorld.");
            else {
                p.sendMessage(ChatColor.GREEN + "PocketWorld creado: " + ChatColor.WHITE + world.getName());
                plugin.lazyTeleport().teleport(p, world.getName(), null);
            }
        });
        return true;
    }

    private boolean reset(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        if (!p.hasPermission("etcworlds.pw.reset")) { noPerm(s); return true; }
        if (a.length < 2 || !a[1].equalsIgnoreCase("confirm")) {
            s.sendMessage(ChatColor.RED + "ATENCIÓN: esto BORRA tu pocketworld y crea uno NUEVO vacío.");
            s.sendMessage(ChatColor.YELLOW + "Confirma con: " + ChatColor.WHITE + "/pw reset confirm");
            return true;
        }
        PocketWorldManager mgr = plugin.pocketWorlds();
        PocketWorld pw = mgr.get(p.getUniqueId());
        if (pw == null) { s.sendMessage(ChatColor.RED + "No tienes pocketworld."); return true; }

        // Si el jugador está en su pw, sacarlo primero al spawn principal
        if (p.getWorld().getName().equalsIgnoreCase(pw.worldName)) {
            World fb = Bukkit.getWorlds().get(0);
            p.teleportAsync(fb.getSpawnLocation());
        }
        s.sendMessage(ChatColor.GRAY + "Borrando " + pw.worldName + "...");
        Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
            mgr.delete(p.getUniqueId());
            s.sendMessage(ChatColor.GRAY + "Generando uno nuevo...");
            mgr.create(p, world -> {
                if (world == null) s.sendMessage(ChatColor.RED + "No se pudo recrear el PocketWorld.");
                else {
                    s.sendMessage(ChatColor.GREEN + "PocketWorld regenerado: " + world.getName());
                    plugin.lazyTeleport().teleport(p, world.getName(), null);
                }
            });
        });
        return true;
    }

    /**
     * /pw delete            -> aviso de uso
     * /pw delete confirm    -> borra tu pocketworld
     */
    private boolean deleteSelfCmd(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        if (!p.hasPermission("etcworlds.pw")) { noPerm(s); return true; }
        if (a.length < 2 || !a[1].equalsIgnoreCase("confirm")) {
            s.sendMessage(ChatColor.RED + "ATENCION: esto BORRA tu pocketworld.");
            s.sendMessage(ChatColor.YELLOW + "Confirma con: " + ChatColor.WHITE + "/pw delete confirm");
            return true;
        }
        return deleteSelf(s);
    }

    private boolean deleteSelf(CommandSender s) {
        Player p = (Player) s;
        PocketWorld pw = plugin.pocketWorlds().get(p.getUniqueId());
        if (pw == null) { s.sendMessage(ChatColor.RED + "No tienes pocketworld."); return true; }
        if (p.getWorld().getName().equalsIgnoreCase(pw.worldName))
            p.teleportAsync(Bukkit.getWorlds().get(0).getSpawnLocation());
        Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
            if (plugin.pocketWorlds().delete(p.getUniqueId()))
                s.sendMessage(ChatColor.GREEN + "Tu PocketWorld fue borrado.");
            else
                s.sendMessage(ChatColor.RED + "No se pudo borrar.");
        });
        return true;
    }

    private void list(CommandSender s) {
        if (!s.hasPermission("etcworlds.pw")) { noPerm(s); return; }
        java.util.Collection<PocketWorld> all = plugin.pocketWorlds().all();
        s.sendMessage(ChatColor.GOLD + "=== PocketWorlds (" + all.size() + ") ===");
        for (PocketWorld pw : all) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(pw.owner);
            String name = op.getName() != null ? op.getName() : pw.owner.toString().substring(0, 8);
            World w = Bukkit.getWorld(pw.worldName);
            String state = w != null ? ChatColor.GREEN + "[cargado]" : ChatColor.DARK_GRAY + "[descargado]";
            s.sendMessage(ChatColor.YELLOW + "  " + name + ChatColor.GRAY + " → " + pw.worldName + " " + state);
        }
    }

    private boolean info(CommandSender s) {
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        PocketWorld pw = plugin.pocketWorlds().get(p.getUniqueId());
        if (pw == null) { s.sendMessage(ChatColor.YELLOW + "No tienes pocketworld. Crea uno con /pw create."); return true; }
        World w = Bukkit.getWorld(pw.worldName);
        s.sendMessage(ChatColor.GOLD + "=== Tu PocketWorld ===");
        s.sendMessage(ChatColor.YELLOW + "  Mundo:       " + ChatColor.WHITE + pw.worldName);
        s.sendMessage(ChatColor.YELLOW + "  Creado:      " + ChatColor.WHITE + new Date(pw.createdAt));
        s.sendMessage(ChatColor.YELLOW + "  Estado:      " + (w != null ? ChatColor.GREEN + "cargado" : ChatColor.GRAY + "descargado"));
        if (w != null) {
            s.sendMessage(ChatColor.YELLOW + "  Jugadores:   " + ChatColor.WHITE + w.getPlayers().size());
            s.sendMessage(ChatColor.YELLOW + "  Chunks:      " + ChatColor.WHITE + w.getLoadedChunks().length);
            s.sendMessage(ChatColor.YELLOW + "  Border:      " + ChatColor.WHITE + (int) w.getWorldBorder().getSize());
        }
        s.sendMessage(ChatColor.YELLOW + "  Invitados:   " + ChatColor.WHITE + pw.invitees.size());
        for (UUID u : pw.invitees) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(u);
            s.sendMessage(ChatColor.GRAY + "    - " + (op.getName() != null ? op.getName() : u.toString().substring(0, 8)));
        }
        return true;
    }

    private boolean invite(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        if (!p.hasPermission("etcworlds.pw")) { noPerm(s); return true; }
        if (a.length < 2) { s.sendMessage(ChatColor.YELLOW + "Uso: /pw invite <jugador>"); return true; }
        PocketWorld pw = plugin.pocketWorlds().get(p.getUniqueId());
        if (pw == null) { s.sendMessage(ChatColor.RED + "No tienes pocketworld."); return true; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(a[1]);
        if (target.getUniqueId().equals(p.getUniqueId())) { s.sendMessage(ChatColor.RED + "No puedes invitarte a ti mismo."); return true; }
        if (plugin.pocketWorlds().invite(p.getUniqueId(), target.getUniqueId())) {
            s.sendMessage(ChatColor.GREEN + a[1] + " puede entrar a tu PocketWorld.");
            Player online = target.getPlayer();
            if (online != null && online.isOnline())
                online.sendMessage(ChatColor.AQUA + p.getName() + " te invitó a su PocketWorld. Usa /pw visit " + p.getName());
        } else {
            s.sendMessage(ChatColor.YELLOW + "Ese jugador ya estaba invitado.");
        }
        return true;
    }

    private boolean kick(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        if (a.length < 2) { s.sendMessage(ChatColor.YELLOW + "Uso: /pw kick <jugador>"); return true; }
        PocketWorld pw = plugin.pocketWorlds().get(p.getUniqueId());
        if (pw == null) { s.sendMessage(ChatColor.RED + "No tienes pocketworld."); return true; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(a[1]);
        if (plugin.pocketWorlds().uninvite(p.getUniqueId(), target.getUniqueId())) {
            s.sendMessage(ChatColor.GREEN + a[1] + " ya no tiene acceso a tu PocketWorld.");
            // Si está dentro, sacarlo
            Player online = target.getPlayer();
            if (online != null && online.isOnline() && online.getWorld().getName().equalsIgnoreCase(pw.worldName)) {
                online.teleportAsync(Bukkit.getWorlds().get(0).getSpawnLocation());
                online.sendMessage(ChatColor.YELLOW + p.getName() + " te quitó del PocketWorld.");
            }
        } else {
            s.sendMessage(ChatColor.YELLOW + "Ese jugador no estaba invitado.");
        }
        return true;
    }

    /**
     * /pw useradd &lt;jugador&gt; — da permiso al jugador de editar las rules de tu pocketworld.
     * Lo agrega tambien como invitee si no lo era (para que pueda entrar y editar dentro).
     */
    private boolean userAdd(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        if (!p.hasPermission("etcworlds.pw")) { noPerm(s); return true; }
        if (a.length < 2) { s.sendMessage(ChatColor.YELLOW + "Uso: /pw useradd <jugador>"); return true; }
        PocketWorld pw = plugin.pocketWorlds().get(p.getUniqueId());
        if (pw == null) { s.sendMessage(ChatColor.RED + "No tienes pocketworld."); return true; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(a[1]);
        if (target.getUniqueId().equals(p.getUniqueId())) { s.sendMessage(ChatColor.RED + "Eres el dueno."); return true; }
        // Garantizar invite (entrada + build) ademas del permiso de rules
        plugin.pocketWorlds().invite(p.getUniqueId(), target.getUniqueId());
        if (plugin.pocketWorlds().addUser(p.getUniqueId(), target.getUniqueId())) {
            s.sendMessage(ChatColor.GREEN + a[1] + " ahora puede editar las rules de tu PocketWorld.");
            Player online = target.getPlayer();
            if (online != null && online.isOnline())
                online.sendMessage(ChatColor.AQUA + p.getName() + " te dio permiso para editar las rules de su PocketWorld. Usa /pw rules estando dentro.");
        } else {
            s.sendMessage(ChatColor.YELLOW + a[1] + " ya tenia permiso de editar rules.");
        }
        return true;
    }

    /** /pw userremove &lt;jugador&gt; — quita el permiso de editar rules (no expulsa, no quita invite). */
    private boolean userRemove(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        if (a.length < 2) { s.sendMessage(ChatColor.YELLOW + "Uso: /pw userremove <jugador>"); return true; }
        PocketWorld pw = plugin.pocketWorlds().get(p.getUniqueId());
        if (pw == null) { s.sendMessage(ChatColor.RED + "No tienes pocketworld."); return true; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(a[1]);
        if (plugin.pocketWorlds().removeUser(p.getUniqueId(), target.getUniqueId()))
            s.sendMessage(ChatColor.GREEN + a[1] + " ya no puede editar las rules de tu PocketWorld.");
        else
            s.sendMessage(ChatColor.YELLOW + a[1] + " no tenia permiso de editar rules.");
        return true;
    }

    private boolean visit(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        if (!p.hasPermission("etcworlds.pw.visit")) { noPerm(s); return true; }
        if (a.length < 2) { s.sendMessage(ChatColor.YELLOW + "Uso: /pw visit <jugador>"); return true; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(a[1]);
        PocketWorld pw = plugin.pocketWorlds().get(target.getUniqueId());
        if (pw == null) { s.sendMessage(ChatColor.RED + a[1] + " no tiene PocketWorld."); return true; }
        boolean canVisit = pw.invitees.contains(p.getUniqueId())
                || target.getUniqueId().equals(p.getUniqueId())
                || p.hasPermission("etcworlds.pw.bypass");
        if (!canVisit) { s.sendMessage(ChatColor.RED + "No estás invitado al PocketWorld de " + a[1] + "."); return true; }
        plugin.lazyTeleport().teleport(p, pw.worldName, null);
        return true;
    }

    private boolean setSpawn(CommandSender s) {
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        PocketWorld pw = plugin.pocketWorlds().get(p.getUniqueId());
        if (pw == null) { s.sendMessage(ChatColor.RED + "No tienes pocketworld."); return true; }
        if (!p.getWorld().getName().equalsIgnoreCase(pw.worldName)) {
            s.sendMessage(ChatColor.RED + "Debes estar dentro de tu PocketWorld.");
            return true;
        }
        var rules = plugin.worlds().getRules(pw.worldName);
        if (rules == null) { s.sendMessage(ChatColor.RED + "Reglas no encontradas."); return true; }
        Location loc = p.getLocation();
        rules.spawnX = loc.getX(); rules.spawnY = loc.getY(); rules.spawnZ = loc.getZ();
        rules.spawnYaw = loc.getYaw(); rules.spawnPitch = loc.getPitch();
        plugin.worlds().saveRules(pw.worldName);
        p.getWorld().setSpawnLocation(loc);
        s.sendMessage(ChatColor.GREEN + "Spawn de tu PocketWorld definido.");
        return true;
    }

    /**
     * /pw rules            -> si estas dentro de un pocketworld del que eres dueno/user/admin,
     *                         abre la GUI de ESE pocketworld. Si no, abre el tuyo.
     * /pw rules &lt;jugador&gt; -> abre la GUI del pocketworld de otro jugador.
     *   Permitido si: eres admin, o eres user (permiso de editar rules) de su pocketworld.
     */
    private boolean rules(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        if (!p.hasPermission("etcworlds.pw")) { noPerm(s); return true; }

        UUID targetUuid;
        if (a.length >= 2) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(a[1]);
            if (op.getUniqueId() == null) { s.sendMessage(ChatColor.RED + "Jugador desconocido."); return true; }
            targetUuid = op.getUniqueId();
            // Permiso: admin OR user del pocketworld destino OR el propio dueno
            if (!targetUuid.equals(p.getUniqueId()) && !p.hasPermission("etcworlds.pw.admin")) {
                PocketWorld targetPw = plugin.pocketWorlds().get(targetUuid);
                if (targetPw == null || !targetPw.users.contains(p.getUniqueId())) {
                    s.sendMessage(ChatColor.RED + "Solo puedes editar rules de pocketworlds donde te dieron permiso (/pw useradd).");
                    return true;
                }
            }
        } else {
            // Sin arg: intentar usar el pocketworld donde estas parado
            PocketWorld here = plugin.pocketWorlds().getByWorldName(p.getWorld().getName());
            if (here != null) {
                boolean allowed = here.owner.equals(p.getUniqueId())
                        || here.users.contains(p.getUniqueId())
                        || p.hasPermission("etcworlds.pw.admin");
                if (allowed) { plugin.pocketRulesGUI().open(p, here.worldName); return true; }
                s.sendMessage(ChatColor.YELLOW + "No tienes permiso para editar rules de este pocketworld. Abriendo el tuyo...");
            }
            targetUuid = p.getUniqueId();
        }

        PocketWorld pw = plugin.pocketWorlds().get(targetUuid);
        if (pw == null) {
            s.sendMessage(ChatColor.RED + (a.length >= 2 ? a[1] + " no tiene" : "No tienes") + " pocketworld.");
            return true;
        }
        plugin.pocketRulesGUI().open(p, pw.worldName);
        return true;
    }

    private boolean admin(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.pw.admin")) { noPerm(s); return true; }
        if (a.length < 3 || !a[1].equalsIgnoreCase("delete")) {
            s.sendMessage(ChatColor.YELLOW + "Uso: /pw admin delete <jugador>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(a[2]);
        PocketWorld pw = plugin.pocketWorlds().get(target.getUniqueId());
        if (pw == null) { s.sendMessage(ChatColor.RED + a[2] + " no tiene pocketworld."); return true; }
        Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
            if (plugin.pocketWorlds().delete(target.getUniqueId()))
                s.sendMessage(ChatColor.GREEN + "PocketWorld de " + a[2] + " borrado.");
            else
                s.sendMessage(ChatColor.RED + "No se pudo borrar.");
        });
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "=== PocketWorld ===");
        s.sendMessage(ChatColor.YELLOW + "/pw                    " + ChatColor.GRAY + "TP a tu pocketworld (crea si no existe)");
        s.sendMessage(ChatColor.YELLOW + "/pw create             " + ChatColor.GRAY + "Crea tu pocketworld");
        s.sendMessage(ChatColor.YELLOW + "/pw reset confirm      " + ChatColor.GRAY + "Borra y regenera el tuyo");
        s.sendMessage(ChatColor.YELLOW + "/pw delete confirm     " + ChatColor.GRAY + "Borra el tuyo");
        s.sendMessage(ChatColor.YELLOW + "/pw info               " + ChatColor.GRAY + "Info de tu pocketworld");
        s.sendMessage(ChatColor.YELLOW + "/pw list               " + ChatColor.GRAY + "Lista todos los pocketworlds");
        s.sendMessage(ChatColor.YELLOW + "/pw invite <jugador>   " + ChatColor.GRAY + "Da acceso (entrar + construir)");
        s.sendMessage(ChatColor.YELLOW + "/pw kick <jugador>     " + ChatColor.GRAY + "Quita acceso");
        s.sendMessage(ChatColor.YELLOW + "/pw useradd <jugador>  " + ChatColor.GRAY + "Permite editar tus rules");
        s.sendMessage(ChatColor.YELLOW + "/pw userremove <jug.>  " + ChatColor.GRAY + "Quita permiso de editar rules");
        s.sendMessage(ChatColor.YELLOW + "/pw visit <jugador>    " + ChatColor.GRAY + "Ir al de otro (si invitado)");
        s.sendMessage(ChatColor.YELLOW + "/pw setspawn           " + ChatColor.GRAY + "Define spawn dentro del tuyo");
        s.sendMessage(ChatColor.YELLOW + "/pw rules              " + ChatColor.GRAY + "GUI de flags del pocketworld donde estas");
        if (s.hasPermission("etcworlds.pw.admin")) {
            s.sendMessage(ChatColor.YELLOW + "/pw rules <jugador>    " + ChatColor.GRAY + "Admin: edita rules del PW de otro");
            s.sendMessage(ChatColor.YELLOW + "/pw admin delete <j>   " + ChatColor.GRAY + "Admin: borra el de otro");
        }
    }

    private void noPerm(CommandSender s) {
        s.sendMessage(ChatColor.RED + plugin.getConfig().getString("messages.no-permission", "Sin permiso."));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        if (a.length == 1) {
            List<String> base = new ArrayList<>(List.of("tp", "create", "reset", "delete", "list", "info",
                    "invite", "kick", "useradd", "userremove", "visit", "setspawn", "rules", "help"));
            if (s.hasPermission("etcworlds.pw.admin")) base.add("admin");
            return base.stream().filter(x -> x.startsWith(a[0].toLowerCase())).toList();
        }
        if (a.length == 2 && (a[0].equalsIgnoreCase("invite")
                || a[0].equalsIgnoreCase("kick") || a[0].equalsIgnoreCase("visit")
                || a[0].equalsIgnoreCase("useradd") || a[0].equalsIgnoreCase("userremove"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return names;
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("reset"))
            return List.of("confirm");
        if (a.length == 2 && a[0].equalsIgnoreCase("delete")) {
            return List.of("confirm");
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("admin")) return List.of("delete");
        if (a.length == 2 && a[0].equalsIgnoreCase("rules") && s.hasPermission("etcworlds.pw.admin")) {
            List<String> names = new ArrayList<>();
            for (PocketWorld pw : plugin.pocketWorlds().all()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(pw.owner);
                if (op.getName() != null) names.add(op.getName());
            }
            return names;
        }
        if (a.length == 3 && a[0].equalsIgnoreCase("admin") && a[1].equalsIgnoreCase("delete")) {
            List<String> names = new ArrayList<>();
            for (PocketWorld pw : plugin.pocketWorlds().all()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(pw.owner);
                if (op.getName() != null) names.add(op.getName());
            }
            return names;
        }
        return List.of();
    }
}
