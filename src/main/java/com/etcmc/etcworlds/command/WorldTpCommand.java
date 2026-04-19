package com.etcmc.etcworlds.command;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.WorldRules;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Comandos rápidos: /world &lt;nombre&gt;, /worldlist, /worldinfo, /worldspawn.
 */
public class WorldTpCommand implements CommandExecutor, TabCompleter {

    private final ETCWorlds plugin;

    public WorldTpCommand(ETCWorlds plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        String name = c.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "world", "mv", "mvtp", "wtp" -> tp(s, a);
            case "worldlist", "worlds", "mvlist" -> list(s);
            case "worldinfo", "winfo", "mvinfo" -> info(s, a);
            case "worldspawn", "setworldspawn", "wspawn" -> setSpawn(s);
            case "spawn" -> spawnWorld(s);
            case "lobby" -> lobbyWorld(s);
            default -> false;
        };
    }

    private boolean tp(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.tp")) { s.sendMessage(ChatColor.RED + "Sin permiso."); return true; }
        if (a.length == 0) {
            s.sendMessage(ChatColor.YELLOW + "Uso: /world <nombre> [jugador]");
            return true;
        }
        Player target;
        if (a.length >= 2) {
            if (!s.hasPermission("etcworlds.tp.others")) { s.sendMessage(ChatColor.RED + "Sin permiso."); return true; }
            target = Bukkit.getPlayer(a[1]);
            if (target == null) { s.sendMessage(ChatColor.RED + "Jugador no encontrado."); return true; }
        } else {
            if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Especifica jugador."); return true; }
            target = p;
        }
        plugin.lazyTeleport().teleport(target, a[0], null);
        return true;
    }

    private boolean list(CommandSender s) {
        if (!s.hasPermission("etcworlds.list")) { s.sendMessage(ChatColor.RED + "Sin permiso."); return true; }
        s.sendMessage(ChatColor.GOLD + "Mundos disponibles:");
        for (String name : plugin.worlds().getManagedNames()) {
            World w = Bukkit.getWorld(name);
            String tag = w != null ? ChatColor.GREEN + "● " : ChatColor.DARK_GRAY + "○ ";
            int players = w != null ? w.getPlayers().size() : 0;
            s.sendMessage(tag + ChatColor.YELLOW + name + ChatColor.GRAY + " (" + players + " jugadores)");
        }
        return true;
    }

    private boolean info(CommandSender s, String[] a) {
        if (!s.hasPermission("etcworlds.info")) { s.sendMessage(ChatColor.RED + "Sin permiso."); return true; }
        String name = a.length >= 1 ? a[0] : (s instanceof Player p ? p.getWorld().getName() : Bukkit.getWorlds().get(0).getName());
        World w = Bukkit.getWorld(name);
        WorldRules r = plugin.worlds().getRules(name);
        s.sendMessage(ChatColor.GOLD + "=== " + name + " ===");
        if (w == null) { s.sendMessage(ChatColor.GRAY + "Descargado."); return true; }
        s.sendMessage(ChatColor.GRAY + "Env: " + w.getEnvironment() + "  Players: " + w.getPlayers().size());
        Location sp = w.getSpawnLocation();
        s.sendMessage(ChatColor.GRAY + "Spawn: " + (int) sp.getX() + " " + (int) sp.getY() + " " + (int) sp.getZ());
        if (r != null) s.sendMessage(ChatColor.GRAY + "Template: " + r.template + " Group: " + r.worldGroup);
        return true;
    }

    private boolean setSpawn(CommandSender s) {
        if (!s.hasPermission("etcworlds.spawn.set")) { s.sendMessage(ChatColor.RED + "Sin permiso."); return true; }
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        String name = p.getWorld().getName();
        WorldRules r = plugin.worlds().getRules(name);
        if (r == null) { s.sendMessage(ChatColor.RED + "Mundo no administrado."); return true; }
        Location loc = p.getLocation();
        r.spawnX = loc.getX(); r.spawnY = loc.getY(); r.spawnZ = loc.getZ();
        r.spawnYaw = loc.getYaw(); r.spawnPitch = loc.getPitch();
        p.getWorld().setSpawnLocation(loc);
        plugin.worlds().saveRules(name);
        s.sendMessage(ChatColor.GREEN + "Spawn definido en " + name + ".");
        return true;
    }

    /** /spawn — teletransporta al spawn del mundo actual del jugador. */
    private boolean spawnWorld(CommandSender s) {
        if (!s.hasPermission("etcworlds.spawn")) { s.sendMessage(ChatColor.RED + "Sin permiso."); return true; }
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        String worldName = p.getWorld().getName();
        WorldRules r = plugin.worlds().getRules(worldName);
        Location spawn = r != null ? r.spawnLocation(p.getWorld()) : p.getWorld().getSpawnLocation();
        plugin.lazyTeleport().teleport(p, worldName, spawn);
        return true;
    }

    /** /lobby — teletransporta al mundo lobby configurado. */
    private boolean lobbyWorld(CommandSender s) {
        if (!s.hasPermission("etcworlds.lobby")) { s.sendMessage(ChatColor.RED + "Sin permiso."); return true; }
        if (!(s instanceof Player p)) { s.sendMessage(ChatColor.RED + "Solo jugadores."); return true; }
        String lobby = plugin.getConfig().getString("lobby-world", "");
        if (lobby.isBlank()) {
            p.sendMessage(ChatColor.RED + "[ETCWorlds] No hay lobby configurado. "
                    + "Define 'lobby-world: <nombre>' en config.yml");
            return true;
        }
        plugin.lazyTeleport().teleport(p, lobby, null);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        if (a.length == 1) return new ArrayList<>(plugin.worlds().getManagedNames());
        return List.of();
    }
}
