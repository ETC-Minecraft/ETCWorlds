package com.etcmc.etcworlds.listener;

import com.etcmc.etcworlds.ETCWorlds;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Bloquea el cambio de mundo si el jugador no tiene acceso (whitelist/blacklist/permiso).
 */
public class WorldAccessListener implements Listener {

    private final ETCWorlds plugin;

    public WorldAccessListener(ETCWorlds plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Location to = e.getTo();
        if (to == null || to.getWorld() == null) return;
        World fromWorld = e.getFrom().getWorld();
        if (fromWorld != null && fromWorld.getName().equals(to.getWorld().getName())) return;

        Player p = e.getPlayer();
        if (!plugin.worlds().canAccess(p, to.getWorld().getName())) {
            e.setCancelled(true);
            String msg = plugin.getConfig().getString("messages.teleport-denied", "&cAcceso denegado.");
            String prefix = plugin.getConfig().getString("messages.prefix", "");
            p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', prefix + msg));
        }
    }
}
