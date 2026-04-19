package com.etcmc.etcworlds.listener;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.CustomPortal;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detecta cuando un jugador pisa un bloque registrado como portal custom
 * y lo teletransporta al mundo/coordenadas de destino.
 *
 * Cooldown de 3s por jugador para evitar bucles de teleport.
 */
public class CustomPortalListener implements Listener {

    private final ETCWorlds plugin;
    /** UUID → timestamp último uso (ms). */
    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 3_000L;

    public CustomPortalListener(ETCWorlds plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        // Solo procesar si el jugador cambió de bloque (optimización)
        Location from = e.getFrom();
        Location to   = e.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;

        World world = to.getWorld();
        if (world == null) return;

        Player p = e.getPlayer();

        // Cooldown para no disparar múltiples veces
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(p.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) return;

        CustomPortal portal = plugin.portals().getAt(
                to.getBlockX(), to.getBlockY(), to.getBlockZ(), world.getName());
        if (portal == null) return;

        // Verificar permiso
        if (!portal.permission.isEmpty() && !p.hasPermission(portal.permission)) {
            p.sendMessage(ChatColor.RED + "No tienes permiso para usar este portal.");
            return;
        }

        cooldowns.put(p.getUniqueId(), now);

        // Mensaje de entrada
        if (!portal.enterMessage.isEmpty()) {
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', portal.enterMessage));
        }

        // Calcular destino
        Location dest = null;
        if (portal.hasCustomDest()) {
            World destWorld = Bukkit.getWorld(portal.destinationWorld);
            if (destWorld != null) {
                dest = new Location(destWorld,
                        portal.destX, portal.destY, portal.destZ,
                        portal.destYaw, portal.destPitch);
            }
        }

        plugin.lazyTeleport().teleport(p, portal.destinationWorld, dest);
    }
}
