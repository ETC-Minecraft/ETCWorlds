package com.etcmc.etcworlds.gui;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.WorldRules;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI minimalista de gestión de mundos.
 * - Click izquierdo: teleport
 * - Shift-click: load/unload toggle
 * - Click derecho: info (envía mensaje de chat)
 *
 * Para ediciones avanzadas se sigue usando /etcworlds set / link.
 */
public class WorldsGUI implements Listener {

    private final ETCWorlds plugin;

    public WorldsGUI(ETCWorlds plugin) { this.plugin = plugin; }

    public void open(Player p) {
        List<String> names = new ArrayList<>(plugin.worlds().getManagedNames());
        int rows = Math.min(6, Math.max(1, (names.size() + 8) / 9));
        Inventory inv = Bukkit.createInventory(new Holder(), rows * 9,
                ChatColor.DARK_AQUA + "ETCWorlds — Mundos");
        int idx = 0;
        for (String name : names) {
            if (idx >= rows * 9) break;
            inv.setItem(idx++, iconFor(name));
        }
        p.openInventory(inv);
    }

    private ItemStack iconFor(String name) {
        World w = Bukkit.getWorld(name);
        WorldRules r = plugin.worlds().getRules(name);
        Material m = w != null ? Material.GRASS_BLOCK : Material.DIRT;
        if (r != null) {
            switch (r.template) {
                case VOID, LAYERED_VOID -> m = Material.GLASS;
                case SKYBLOCK -> m = Material.OAK_SAPLING;
                case ONEBLOCK -> m = Material.GRASS_BLOCK;
                case AMPLIFIED -> m = Material.GOAT_SPAWN_EGG;
                case FLAT -> m = Material.SNOW_BLOCK;
                case FLOATING_ISLANDS -> m = Material.FEATHER;
                case SINGLE_BIOME -> m = Material.SAND;
                case CUSTOM_HEIGHT -> m = Material.LADDER;
                default -> {}
            }
            if (r.environment == World.Environment.NETHER) m = Material.NETHERRACK;
            if (r.environment == World.Environment.THE_END) m = Material.END_STONE;
        }
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + name + (w != null ? ChatColor.GREEN + " ●" : ""));
            List<String> lore = new ArrayList<>();
            if (r != null) {
                lore.add(ChatColor.GRAY + "Template: " + ChatColor.WHITE + r.template);
                lore.add(ChatColor.GRAY + "Env: " + ChatColor.WHITE + r.environment);
                lore.add(ChatColor.GRAY + "Group: " + ChatColor.WHITE + r.worldGroup);
                if (r.isTemplate) lore.add(ChatColor.LIGHT_PURPLE + "[plantilla]");
                if (r.perPlayerInstance) lore.add(ChatColor.LIGHT_PURPLE + "[instanciable]");
            }
            if (w != null) lore.add(ChatColor.GRAY + "Jugadores: " + w.getPlayers().size());
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click izq: " + ChatColor.WHITE + "teleport");
            lore.add(ChatColor.YELLOW + "Click der: " + ChatColor.WHITE + "info");
            lore.add(ChatColor.YELLOW + "Shift-click: " + ChatColor.WHITE + "load/unload");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta() || it.getItemMeta().getDisplayName().isEmpty()) return;
        String name = ChatColor.stripColor(it.getItemMeta().getDisplayName())
                .replace(" ●", "").trim();
        if (e.getClick() == ClickType.RIGHT) {
            p.closeInventory();
            p.performCommand("etcworlds info " + name);
        } else if (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT) {
            World w = Bukkit.getWorld(name);
            if (w != null && w.getPlayers().isEmpty()) {
                final String finalName = name;
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                    plugin.worlds().unloadWorld(finalName, true);
                    p.sendMessage(ChatColor.GRAY + "Descargado " + finalName);
                    p.getScheduler().run(plugin, t -> open(p), null);
                });
            } else if (w == null) {
                final String finalName = name;
                p.sendMessage(ChatColor.GRAY + "Cargando " + finalName + "...");
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                    plugin.worlds().loadWorld(finalName);
                    p.getScheduler().run(plugin, t -> open(p), null);
                });
            } else {
                p.sendMessage(ChatColor.RED + "Hay jugadores en ese mundo.");
            }
        } else {
            p.closeInventory();
            plugin.lazyTeleport().teleport(p, name, null);
        }
    }

    /** Holder-token para identificar nuestro inventario. */
    public static class Holder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return Bukkit.createInventory(this, 9); }
    }
}
