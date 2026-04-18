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
        if (e.getFrom() == null || e.getFrom().getWorld() == null) return;
        WorldRules r = plugin.worlds().getRules(e.getFrom().getWorld().getName());
        if (r == null) return;
        String targetName = switch (e.getCause()) {
            case NETHER_PORTAL -> r.linkedNether;
            case END_PORTAL, END_GATEWAY -> r.linkedEnd;
            default -> null;
        };
        if (targetName == null || targetName.isBlank()) return;

        World target = Bukkit.getWorld(targetName);
        if (target == null && plugin.worlds().isManaged(targetName)) {
            // Mundo descargado: cancelar el evento vainilla y cargar+teletransportar manualmente
            e.setCancelled(true);
            loadAndTeleport(e.getPlayer(), targetName, e.getFrom(), e.getCause());
            return;
        }
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
        if (target == null) {
            // Mundo registrado pero descargado: no podemos llamar loadWorld aquí (hilo de evento ≠ hilo global).
            // Cancelamos el portal si el mundo no está cargado (el jugador puede reintentarlo
            // o el idle-unloader lo carga cuando corresponda).
            if (!plugin.worlds().isManaged(targetName)) return null;
            // No retornamos null sin más — cancelar el evento en el llamador
            return null;
        }

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

    /** Carga el mundo en el hilo global y luego teletransporta al jugador. */
    private void loadAndTeleport(org.bukkit.entity.Player p, String targetName, Location from, TeleportCause cause) {
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            World w = Bukkit.getWorld(targetName);
            if (w == null) w = plugin.worlds().loadWorld(targetName);
            if (w == null) return;
            World target = w;

            double scale = 1.0;
            if (cause == TeleportCause.NETHER_PORTAL) {
                if (from.getWorld().getEnvironment() == World.Environment.NORMAL
                        && target.getEnvironment() == World.Environment.NETHER) scale = 0.125;
                else if (from.getWorld().getEnvironment() == World.Environment.NETHER
                        && target.getEnvironment() == World.Environment.NORMAL) scale = 8.0;
            }
            Location dest = new Location(target, from.getX() * scale, from.getY(), from.getZ() * scale);
            plugin.lazyTeleport().teleport(p, targetName, dest);
        });
    }
}
