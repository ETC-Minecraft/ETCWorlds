package com.etcmc.etcworlds.listener;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.WorldTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Random;

/**
 * Listener simple para mundos OneBlock: cuando rompes el bloque en (0,Y,0)
 * se regenera un nuevo bloque aleatorio (de una pequeña pool).
 *
 * Pool simple ponderada: stone, dirt, grass, andesite, oak_log, coal_ore, iron_ore...
 */
public class OneblockListener implements Listener {

    private final ETCWorlds plugin;
    private final Random rand = new Random();
    private static final Material[] POOL = {
            Material.STONE, Material.STONE, Material.STONE, Material.STONE,
            Material.DIRT, Material.DIRT, Material.GRASS_BLOCK, Material.GRAVEL,
            Material.SAND, Material.OAK_LOG, Material.COBBLESTONE,
            Material.COAL_ORE, Material.COPPER_ORE, Material.IRON_ORE, Material.GOLD_ORE,
            Material.OAK_LEAVES, Material.MOSSY_COBBLESTONE, Material.ANDESITE
    };

    public OneblockListener(ETCWorlds plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        var rules = plugin.worlds().getRules(e.getBlock().getWorld().getName());
        if (rules == null || rules.template != WorldTemplate.ONEBLOCK) return;
        Block b = e.getBlock();
        if (b.getX() != 0 || b.getZ() != 0) return;
        Material next = POOL[rand.nextInt(POOL.length)];
        // Folia: programar en la región del chunk
        b.getWorld().getChunkAt(b).getPersistentDataContainer(); // touch chunk
        Bukkit.getRegionScheduler().run(plugin, b.getLocation(), task ->
                b.getLocation().getBlock().setType(next));
    }
}
