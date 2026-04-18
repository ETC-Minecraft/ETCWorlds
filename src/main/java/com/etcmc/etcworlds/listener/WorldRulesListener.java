package com.etcmc.etcworlds.listener;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.WorldRules;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Locale;

/**
 * Aplica las reglas por-mundo a eventos del jugador:
 * gamemode forzado, fly forzado, build/break, fall-damage, hunger,
 * y filtro de comandos por mundo.
 */
public class WorldRulesListener implements Listener {

    private final ETCWorlds plugin;

    public WorldRulesListener(ETCWorlds plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        applyRulesTo(e.getPlayer());
    }

    @EventHandler
    public void onChangeWorld(PlayerChangedWorldEvent e) {
        applyRulesTo(e.getPlayer());
    }

    private void applyRulesTo(Player p) {
        WorldRules r = plugin.worlds().getRules(p.getWorld().getName());
        if (r == null) return;
        if (r.gamemode != null && p.getGameMode() != r.gamemode
                && !p.hasPermission("etcworlds.bypass.gamemode"))
            p.setGameMode(r.gamemode);
        if (r.fly && !p.getAllowFlight()) {
            p.setAllowFlight(true); p.setFlying(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        WorldRules r = plugin.worlds().getRules(e.getPlayer().getWorld().getName());
        if (r != null && !r.buildEnabled && !e.getPlayer().hasPermission("etcworlds.bypass.access"))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        WorldRules r = plugin.worlds().getRules(e.getPlayer().getWorld().getName());
        if (r != null && !r.buildEnabled && !e.getPlayer().hasPermission("etcworlds.bypass.access"))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(e.getEntity() instanceof Player p)) return;
        WorldRules r = plugin.worlds().getRules(p.getWorld().getName());
        if (r != null && !r.fallDamage) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        WorldRules r = plugin.worlds().getRules(p.getWorld().getName());
        if (r != null && !r.hunger) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        WorldRules r = plugin.worlds().getRules(e.getPlayer().getWorld().getName());
        if (r == null) return;
        String raw = e.getMessage().toLowerCase(Locale.ROOT);
        String cmd = raw.startsWith("/") ? raw.substring(1).split(" ")[0] : raw.split(" ")[0];
        // Si hay whitelist no vacía y no contiene el comando -> bloquear
        if (!r.commandWhitelist.isEmpty() && r.commandWhitelist.stream().noneMatch(c -> c.equalsIgnoreCase(cmd))) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cEse comando no está permitido en este mundo.");
            return;
        }
        if (r.commandBlacklist.stream().anyMatch(c -> c.equalsIgnoreCase(cmd))) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cEse comando está bloqueado en este mundo.");
        }
    }
}
