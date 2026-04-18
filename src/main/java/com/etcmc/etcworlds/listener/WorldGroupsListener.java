package com.etcmc.etcworlds.listener;

import com.etcmc.etcworlds.ETCWorlds;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Maneja el guardado/carga de inventarios al cambiar de mundo entre grupos distintos.
 */
public class WorldGroupsListener implements Listener {

    private final ETCWorlds plugin;

    public WorldGroupsListener(ETCWorlds plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangeWorld(PlayerChangedWorldEvent e) {
        String oldGroup = plugin.groups().groupOf(e.getFrom().getName());
        String newGroup = plugin.groups().groupOf(e.getPlayer().getWorld().getName());
        plugin.groups().switchSnapshot(e.getPlayer(), oldGroup, newGroup);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        String group = plugin.groups().groupOf(e.getPlayer().getWorld().getName());
        plugin.groups().save(e.getPlayer(), group);
    }
}
