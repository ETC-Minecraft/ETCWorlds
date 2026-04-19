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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI tipo "residence flags" para gestionar las reglas de un pocketworld.
 *
 * Cada item representa un flag. Click izquierdo alterna ON/OFF.
 * Algunos flags estan marcados como ADMIN_ONLY (solo OPs / etcworlds.pw.admin).
 * Para no-admins, el item se muestra con barrera y no se puede alternar.
 */
public class PocketRulesGUI implements Listener {

    private static final org.bukkit.NamespacedKey KEY_FLAG = new org.bukkit.NamespacedKey("etcworlds", "pwflag");
    private static final org.bukkit.NamespacedKey KEY_WORLD = new org.bukkit.NamespacedKey("etcworlds", "pwworld");

    private final ETCWorlds plugin;

    public PocketRulesGUI(ETCWorlds plugin) { this.plugin = plugin; }

    public void open(Player viewer, String worldName) {
        WorldRules r = plugin.worlds().getRules(worldName);
        if (r == null) {
            viewer.sendMessage(ChatColor.RED + "Reglas no encontradas para " + worldName);
            return;
        }
        Holder holder = new Holder(worldName);
        Inventory inv = Bukkit.createInventory(holder, 27,
                ChatColor.DARK_AQUA + "Rules: " + ChatColor.WHITE + worldName);

        boolean isAdmin = viewer.hasPermission("etcworlds.pw.admin") || viewer.isOp();

        // Layout: filas de flags
        inv.setItem(0,  flagItem(worldName, "pvp",            r.pvp,                  Material.IRON_SWORD,        "PvP",                "Permite combate entre jugadores", false, isAdmin));
        inv.setItem(1,  flagItem(worldName, "build",          r.buildEnabled,         Material.BRICKS,            "Construir/Romper",   "Permite a TODOS poner/romper bloques\n(los invitados ya pueden por defecto)", false, isAdmin));
        inv.setItem(2,  flagItem(worldName, "interact",       r.interactEnabled,      Material.OAK_DOOR,          "Interactuar",        "Abrir cofres, puertas, palancas, etc.\n(invitados ya pueden por defecto)", false, isAdmin));
        inv.setItem(3,  flagItem(worldName, "fly",            r.fly,                  Material.FEATHER,           "Vuelo",              "Otorga vuelo automatico al entrar", false, isAdmin));
        inv.setItem(4,  flagItem(worldName, "fall-damage",    r.fallDamage,           Material.LEATHER_BOOTS,     "Dano por caida",     "Si OFF: caer no hace dano", false, isAdmin));
        inv.setItem(5,  flagItem(worldName, "hunger",         r.hunger,               Material.COOKED_BEEF,       "Hambre",             "Si OFF: nunca pierdes hambre", false, isAdmin));
        inv.setItem(6,  flagItem(worldName, "mob-spawn",      r.mobSpawn,             Material.ZOMBIE_HEAD,       "Spawn de monstruos", "Permite que aparezcan mobs hostiles", false, isAdmin));
        inv.setItem(7,  flagItem(worldName, "animal-spawn",   r.animalSpawn,          Material.WHEAT,             "Spawn de animales",  "Permite que aparezcan animales", false, isAdmin));
        inv.setItem(8,  flagItem(worldName, "weather-locked", r.weatherLocked,        Material.WATER_BUCKET,      "Clima bloqueado",    "Congela el clima actual", false, isAdmin));

        inv.setItem(9,  flagItem(worldName, "time-locked",    r.timeLocked,           Material.CLOCK,             "Hora bloqueada",     "Congela el ciclo dia/noche", false, isAdmin));

        // Admin-only
        inv.setItem(13, flagItem(worldName, "keep-inventory", r.keepInventoryOnDeath, Material.TOTEM_OF_UNDYING,  "Keep Inventory",     "Mantener inventario al morir.\nADMIN-ONLY (anti-trampas).", true,  isAdmin));
        inv.setItem(14, flagItem(worldName, "immediate-respawn", r.immediateRespawn,  Material.RESPAWN_ANCHOR,    "Respawn inmediato",  "Saltarse la pantalla 'You died'.\nADMIN-ONLY.", true,  isAdmin));

        // Info / cerrar
        inv.setItem(22, infoItem(worldName, r));
        inv.setItem(26, closeItem());

        viewer.openInventory(inv);
    }

    private ItemStack flagItem(String world, String flagId, boolean state, Material mat,
                                String name, String desc, boolean adminOnly, boolean viewerIsAdmin) {
        Material mUse = mat;
        boolean locked = adminOnly && !viewerIsAdmin;
        if (locked) mUse = Material.BARRIER;

        ItemStack it = new ItemStack(mUse);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            String prefix = state ? ChatColor.GREEN + "[ON] " : ChatColor.RED + "[OFF] ";
            meta.setDisplayName(prefix + ChatColor.WHITE + name);
            List<String> lore = new ArrayList<>();
            for (String line : desc.split("\n"))
                lore.add(ChatColor.GRAY + line);
            lore.add("");
            if (adminOnly) lore.add(ChatColor.GOLD + "ADMIN-ONLY");
            if (locked) {
                lore.add(ChatColor.DARK_RED + "No puedes cambiarlo.");
            } else {
                lore.add(ChatColor.YELLOW + "Click para alternar");
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(KEY_FLAG, PersistentDataType.STRING, flagId);
            meta.getPersistentDataContainer().set(KEY_WORLD, PersistentDataType.STRING, world);
            if (state) meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                    org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack infoItem(String world, WorldRules r) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + world);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Template: " + ChatColor.WHITE + r.template);
            lore.add(ChatColor.GRAY + "Border:   " + ChatColor.WHITE + (int) r.borderSize);
            World w = Bukkit.getWorld(world);
            if (w != null) lore.add(ChatColor.GRAY + "Jugadores: " + ChatColor.WHITE + w.getPlayers().size());
            meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack closeItem() {
        ItemStack it = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Cerrar");
            it.setItemMeta(meta);
        }
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        ItemMeta meta = it.getItemMeta();

        if (it.getType() == Material.RED_STAINED_GLASS_PANE) { p.closeInventory(); return; }
        if (it.getType() == Material.BARRIER) {
            p.sendMessage(ChatColor.RED + "Solo un admin puede cambiar este flag.");
            return;
        }
        String flagId = meta.getPersistentDataContainer().get(KEY_FLAG, PersistentDataType.STRING);
        String world  = meta.getPersistentDataContainer().get(KEY_WORLD, PersistentDataType.STRING);
        if (flagId == null || world == null) return;

        // Re-validar permisos en el momento del click
        if (!canEdit(p, world, flagId)) {
            p.sendMessage(ChatColor.RED + "Sin permiso para cambiar " + flagId + ".");
            return;
        }

        WorldRules r = plugin.worlds().getRules(world);
        if (r == null) { p.sendMessage(ChatColor.RED + "Reglas no encontradas."); return; }

        boolean newVal = !readFlag(r, flagId);
        writeFlag(r, flagId, newVal);
        plugin.worlds().saveRules(world);
        World wb = Bukkit.getWorld(world);
        if (wb != null) {
            // setGameRule en Folia exige el global region thread
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> plugin.worlds().applyRules(wb, r));
        }

        p.sendMessage(ChatColor.GRAY + "Flag " + ChatColor.WHITE + flagId + ChatColor.GRAY + " = "
                + (newVal ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
        // Reabrir para refrescar visual
        open(p, world);
    }

    private boolean canEdit(Player p, String world, String flagId) {
        boolean admin = p.hasPermission("etcworlds.pw.admin") || p.isOp();
        if (admin) return true;
        // Admin-only flags
        if (flagId.equals("keep-inventory") || flagId.equals("immediate-respawn")) return false;
        // Resto: dueno o usuario con permiso de editar rules (no cualquier invitee)
        if (plugin.pocketWorlds() == null) return false;
        var pw = plugin.pocketWorlds().getByWorldName(world);
        if (pw == null) return false;
        return pw.owner.equals(p.getUniqueId()) || pw.users.contains(p.getUniqueId());
    }

    private boolean readFlag(WorldRules r, String id) {
        return switch (id) {
            case "pvp" -> r.pvp;
            case "build" -> r.buildEnabled;
            case "interact" -> r.interactEnabled;
            case "fly" -> r.fly;
            case "fall-damage" -> r.fallDamage;
            case "hunger" -> r.hunger;
            case "mob-spawn" -> r.mobSpawn;
            case "animal-spawn" -> r.animalSpawn;
            case "weather-locked" -> r.weatherLocked;
            case "time-locked" -> r.timeLocked;
            case "keep-inventory" -> r.keepInventoryOnDeath;
            case "immediate-respawn" -> r.immediateRespawn;
            default -> false;
        };
    }

    private void writeFlag(WorldRules r, String id, boolean v) {
        switch (id) {
            case "pvp" -> r.pvp = v;
            case "build" -> r.buildEnabled = v;
            case "interact" -> r.interactEnabled = v;
            case "fly" -> r.fly = v;
            case "fall-damage" -> r.fallDamage = v;
            case "hunger" -> r.hunger = v;
            case "mob-spawn" -> r.mobSpawn = v;
            case "animal-spawn" -> r.animalSpawn = v;
            case "weather-locked" -> r.weatherLocked = v;
            case "time-locked" -> r.timeLocked = v;
            case "keep-inventory" -> r.keepInventoryOnDeath = v;
            case "immediate-respawn" -> r.immediateRespawn = v;
        }
    }

    public static class Holder implements InventoryHolder {
        private final String world;
        public Holder(String world) { this.world = world; }
        public String world() { return world; }
        @Override public @NotNull Inventory getInventory() { return Bukkit.createInventory(this, 27); }
    }
}
