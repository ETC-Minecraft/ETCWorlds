package com.etcmc.etcworlds.listener;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.WorldRules;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

/**
 * Reescribe el destino de los portales (nether/end) según WorldRules.linkedNether/linkedEnd.
 * Permite que varios mundos compartan el mismo nether o que cada mundo apunte a uno propio.
 */
public class PortalLinkListener implements Listener {

    private final ETCWorlds plugin;

    public PortalLinkListener(ETCWorlds plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent e) {
        Location to = redirect(e.getFrom(), e.getCause());
        if (to != null) e.setTo(to);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        Location to = redirect(e.getFrom(), TeleportCause.NETHER_PORTAL);
        if (to != null) e.setTo(to);
    }

    private Location redirect(Location from, TeleportCause cause) {
        if (from == null || from.getWorld() == null) return null;
        WorldRules r = plugin.worlds().getRules(from.getWorld().getName());
        if (r == null) return null;
        String targetName = null;
        switch (cause) {
            case NETHER_PORTAL -> targetName = r.linkedNether;
            case END_PORTAL, END_GATEWAY -> targetName = r.linkedEnd;
            default -> { return null; }
        }
        if (targetName == null || targetName.isBlank()) return null;
        World target = Bukkit.getWorld(targetName);
        if (target == null) target = plugin.worlds().loadWorld(targetName);
        if (target == null) return null;

        // Escala vanilla: 1:8 al ir overworld→nether, 8:1 al volver
        double scale = 1.0;
        if (cause == TeleportCause.NETHER_PORTAL) {
            if (from.getWorld().getEnvironment() == World.Environment.NORMAL
                    && target.getEnvironment() == World.Environment.NETHER) scale = 0.125;
            else if (from.getWorld().getEnvironment() == World.Environment.NETHER
                    && target.getEnvironment() == World.Environment.NORMAL) scale = 8.0;
        }
        return new Location(target, from.getX() * scale, from.getY(), from.getZ() * scale);
    }
}
