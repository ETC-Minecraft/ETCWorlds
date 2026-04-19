package com.etcmc.etcworlds.listener;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.CustomPortal;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listens for player movement and triggers custom block-based portals.
 */
public class CustomPortalListener implements Listener {

    private final ETCWorlds plugin;
    /** Per-player cooldown (ms) to avoid repeated teleport loops. */
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private static final long COOLDOWN_MS = 3000L;

    public CustomPortalListener(ETCWorlds plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Only process if the player moved to a new block
        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;

        Player player = event.getPlayer();
        String world  = player.getWorld().getName();
        int x = to.getBlockX();
        int y = to.getBlockY();
        int z = to.getBlockZ();

        CustomPortal portal = plugin.portals().getAt(x, y, z, world);
        if (portal == null) return;

        // Cooldown check
        long now = System.currentTimeMillis();
        Long last = cooldown.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) return;
        cooldown.put(player.getUniqueId(), now);

        // Permission check
        if (!portal.permission.isEmpty() && !player.hasPermission(portal.permission)) {
            player.sendMessage(ChatColor.RED + "No tienes permiso para usar este portal.");
            return;
        }

        // Enter message
        if (!portal.enterMessage.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', portal.enterMessage));
        }

        // Teleport
        Location dest = null;
        if (portal.hasCustomDest()) {
            org.bukkit.World destWorld = org.bukkit.Bukkit.getWorld(portal.destinationWorld);
            if (destWorld != null) {
                dest = new Location(destWorld, portal.destX, portal.destY, portal.destZ,
                        portal.destYaw, portal.destPitch);
            }
        }
        plugin.lazyTeleport().teleport(player, portal.destinationWorld, dest);
    }
}
