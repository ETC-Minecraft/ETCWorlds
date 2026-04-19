package com.etcmc.etcworlds.listener;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.WorldRules;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
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
import org.bukkit.event.player.PlayerRespawnEvent;

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
        Player p = e.getPlayer();
        // Mensaje de salida del mundo anterior
        WorldRules old = plugin.worlds().getRules(e.getFrom().getName());
        if (old != null && !old.exitMessage.isEmpty())
            p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    old.exitMessage.replace("{world}", e.getFrom().getName())));
        applyRulesTo(p);
        // Mensaje de entrada al nuevo mundo
        WorldRules next = plugin.worlds().getRules(p.getWorld().getName());
        if (next != null && !next.enterMessage.isEmpty())
            p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    next.enterMessage.replace("{world}", p.getWorld().getName())));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (e.isBedSpawn() || e.isAnchorSpawn()) return;
        WorldRules r = plugin.worlds().getRules(e.getPlayer().getWorld().getName());
        if (r == null) return;
        Location spawn = r.spawnLocation(e.getPlayer().getWorld());
        if (spawn != null) e.setRespawnLocation(spawn);
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
        // Hunger auto-fill al entrar si hunger=false
        if (!r.hunger) {
            p.setFoodLevel(20);
            p.setSaturation(20f);
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
