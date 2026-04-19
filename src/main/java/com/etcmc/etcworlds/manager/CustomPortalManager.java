package com.etcmc.etcworlds.manager;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.CustomPortal;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages custom block-based portals stored in plugins/ETCWorlds/portals.yml.
 */
public class CustomPortalManager {

    private final ETCWorlds plugin;
    private final File file;
    private final Map<String, CustomPortal> portals = new LinkedHashMap<>();

    public CustomPortalManager(ETCWorlds plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "portals.yml");
    }

    public void load() {
        portals.clear();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("portals");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            ConfigurationSection p = sec.getConfigurationSection(name);
            if (p == null) continue;
            CustomPortal portal = new CustomPortal();
            portal.name             = name;
            portal.triggerWorld     = p.getString("trigger.world", "");
            portal.triggerX         = p.getInt("trigger.x");
            portal.triggerY         = p.getInt("trigger.y");
            portal.triggerZ         = p.getInt("trigger.z");
            portal.destinationWorld = p.getString("destination.world", "");
            if (p.contains("destination.x")) {
                portal.destX     = p.getDouble("destination.x");
                portal.destY     = p.getDouble("destination.y");
                portal.destZ     = p.getDouble("destination.z");
                portal.destYaw   = (float) p.getDouble("destination.yaw", 0);
                portal.destPitch = (float) p.getDouble("destination.pitch", 0);
            }
            portal.permission    = p.getString("permission", "");
            portal.enterMessage  = p.getString("enter-message", "");
            portals.put(name.toLowerCase(), portal);
        }
        plugin.getLogger().info("[ETCWorlds] Loaded " + portals.size() + " custom portal(s).");
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (CustomPortal portal : portals.values()) {
            String base = "portals." + portal.name;
            cfg.set(base + ".trigger.world", portal.triggerWorld);
            cfg.set(base + ".trigger.x", portal.triggerX);
            cfg.set(base + ".trigger.y", portal.triggerY);
            cfg.set(base + ".trigger.z", portal.triggerZ);
            cfg.set(base + ".destination.world", portal.destinationWorld);
            if (portal.hasCustomDest()) {
                cfg.set(base + ".destination.x",     portal.destX);
                cfg.set(base + ".destination.y",     portal.destY);
                cfg.set(base + ".destination.z",     portal.destZ);
                cfg.set(base + ".destination.yaw",   portal.destYaw);
                cfg.set(base + ".destination.pitch", portal.destPitch);
            }
            if (!portal.permission.isEmpty())   cfg.set(base + ".permission",    portal.permission);
            if (!portal.enterMessage.isEmpty())  cfg.set(base + ".enter-message", portal.enterMessage);
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[ETCWorlds] Failed to save portals.yml: " + e.getMessage());
        }
    }

    public void add(CustomPortal portal) {
        portals.put(portal.name.toLowerCase(), portal);
        save();
    }

    public boolean remove(String name) {
        boolean removed = portals.remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    /** Returns the portal whose trigger block matches the given coordinates, or null. */
    public CustomPortal getAt(int x, int y, int z, String world) {
        for (CustomPortal p : portals.values()) {
            if (p.triggerX == x && p.triggerY == y && p.triggerZ == z
                    && p.triggerWorld.equalsIgnoreCase(world)) {
                return p;
            }
        }
        return null;
    }

    public CustomPortal get(String name) {
        return portals.get(name.toLowerCase());
    }

    public Collection<CustomPortal> all() {
        return new ArrayList<>(portals.values());
    }

    public int count() {
        return portals.size();
    }
}
