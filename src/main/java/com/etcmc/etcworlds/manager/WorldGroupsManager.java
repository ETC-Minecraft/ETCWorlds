package com.etcmc.etcworlds.manager;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.WorldRules;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

/**
 * Sistema de "world groups" estilo PerWorldInventory.
 * Mundos del mismo grupo (worldGroup en WorldRules) comparten:
 *   inventario, armadura, enderchest, XP, niveles, vida, hambre, gamemode (si forzado).
 *
 * Mundos en grupos distintos guardan/restauran al cambiar de mundo.
 *
 * Almacenamiento: plugins/ETCWorlds/groupdata/&lt;group&gt;/&lt;uuid&gt;.yml
 */
public class WorldGroupsManager {

    private final ETCWorlds plugin;

    public WorldGroupsManager(ETCWorlds plugin) { this.plugin = plugin; }

    public String groupOf(String worldName) {
        WorldRules r = plugin.worlds().getRules(worldName);
        return r != null ? r.worldGroup : "default";
    }

    /** Llamado al cambiar de mundo si los grupos difieren. Guarda el snapshot del mundo viejo y carga el del nuevo. */
    public void switchSnapshot(Player p, String oldWorldGroup, String newWorldGroup) {
        if (oldWorldGroup.equals(newWorldGroup)) return;
        save(p, oldWorldGroup);
        load(p, newWorldGroup);
    }

    public void save(Player p, String group) {
        File file = fileFor(p.getUniqueId(), group);
        file.getParentFile().mkdirs();
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.set("inventory", encodeItems(p.getInventory().getContents()));
            y.set("armor", encodeItems(p.getInventory().getArmorContents()));
            y.set("enderchest", encodeItems(p.getEnderChest().getContents()));
        } catch (IOException ex) { plugin.getLogger().warning("Encode falló: " + ex); }
        y.set("xp", p.getExp());
        y.set("level", p.getLevel());
        y.set("health", p.getHealth());
        y.set("food", p.getFoodLevel());
        y.set("saturation", p.getSaturation());
        y.set("gamemode", p.getGameMode().name());
        try { y.save(file); } catch (IOException ex) { plugin.getLogger().warning("Save group falló: " + ex); }
    }

    public void load(Player p, String group) {
        File file = fileFor(p.getUniqueId(), group);
        PlayerInventory inv = p.getInventory();
        if (!file.exists()) {
            // Primera vez en este grupo: limpiar
            inv.clear();
            inv.setArmorContents(new ItemStack[4]);
            p.getEnderChest().clear();
            p.setExp(0); p.setLevel(0);
            p.setFoodLevel(20); p.setSaturation(5);
            var maxAttr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxAttr != null) p.setHealth(maxAttr.getValue());
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        try {
            inv.setContents(decodeItems(y.getString("inventory", "")));
            inv.setArmorContents(decodeItems(y.getString("armor", "")));
            p.getEnderChest().setContents(decodeItems(y.getString("enderchest", "")));
        } catch (IOException ex) { plugin.getLogger().warning("Decode falló: " + ex); }
        p.setExp((float) y.getDouble("xp", 0));
        p.setLevel(y.getInt("level", 0));
        var maxAttr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : 20.0;
        p.setHealth(Math.min(max, y.getDouble("health", max)));
        p.setFoodLevel(y.getInt("food", 20));
        p.setSaturation((float) y.getDouble("saturation", 5));
        try {
            GameMode gm = GameMode.valueOf(y.getString("gamemode", "SURVIVAL"));
            p.setGameMode(gm);
        } catch (Exception ignored) {}
    }

    private File fileFor(UUID uuid, String group) {
        return new File(plugin.getDataFolder(), "groupdata/" + group + "/" + uuid + ".yml");
    }

    private static String encodeItems(ItemStack[] items) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream boos = new BukkitObjectOutputStream(bos)) {
            boos.writeInt(items.length);
            for (ItemStack i : items) boos.writeObject(i);
            boos.flush();
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        }
    }

    private static ItemStack[] decodeItems(String b64) throws IOException {
        if (b64 == null || b64.isBlank()) return new ItemStack[0];
        byte[] data = Base64.getDecoder().decode(b64);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             BukkitObjectInputStream bois = new BukkitObjectInputStream(bis)) {
            int n = bois.readInt();
            ItemStack[] out = new ItemStack[n];
            for (int i = 0; i < n; i++) out[i] = (ItemStack) bois.readObject();
            return out;
        } catch (ClassNotFoundException e) { throw new IOException(e); }
    }
}
