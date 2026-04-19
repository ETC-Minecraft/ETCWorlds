package com.etcmc.etcworlds.manager;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.CustomPortal;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gestiona los portales custom (bloques que teletransportan a otro mundo).
 * Se persisten en plugins/ETCWorlds/portals.yml.
 *
 * Uso:
 *   /ecw portal create <nombre> <mundoDestino> [x y z yaw]
 *   /ecw portal delete <nombre>
 *   /ecw portal list
 *   /ecw portal tp <nombre>
 */
public class CustomPortalManager {

    private final ETCWorlds plugin;
    private final Map<String, CustomPortal> portals = new LinkedHashMap<>();
    private File file;

    public CustomPortalManager(ETCWorlds plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "portals.yml");
    }

    public void load() {
        portals.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yaml.getConfigurationSection("portals");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            ConfigurationSection p = sec.getConfigurationSection(name);
            if (p == null) continue;
            CustomPortal portal = new CustomPortal();
            portal.name = name;
            portal.triggerWorld = p.getString("trigger.world", "");
            portal.triggerX     = p.getInt("trigger.x", 0);
            portal.triggerY     = p.getInt("trigger.y", 0);
            portal.triggerZ     = p.getInt("trigger.z", 0);
            portal.destinationWorld = p.getString("destination.world", "");
            portal.destX   = p.getDouble("destination.x", Double.MAX_VALUE);
            portal.destY   = p.getDouble("destination.y", Double.MAX_VALUE);
            portal.destZ   = p.getDouble("destination.z", Double.MAX_VALUE);
            portal.destYaw   = (float) p.getDouble("destination.yaw", 0);
            portal.destPitch = (float) p.getDouble("destination.pitch", 0);
            portal.permission    = p.getString("permission", "");
            portal.enterMessage  = p.getString("enter-message", "");
            portals.put(name.toLowerCase(), portal);
        }
        plugin.getLogger().info("Portales custom cargados: " + portals.size());
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (CustomPortal p : portals.values()) {
            String base = "portals." + p.name;
            yaml.set(base + ".trigger.world", p.triggerWorld);
            yaml.set(base + ".trigger.x", p.triggerX);
            yaml.set(base + ".trigger.y", p.triggerY);
            yaml.set(base + ".trigger.z", p.triggerZ);
            yaml.set(base + ".destination.world", p.destinationWorld);
            if (p.hasCustomDest()) {
                yaml.set(base + ".destination.x",     p.destX);
                yaml.set(base + ".destination.y",     p.destY);
                yaml.set(base + ".destination.z",     p.destZ);
                yaml.set(base + ".destination.yaw",   (double) p.destYaw);
                yaml.set(base + ".destination.pitch", (double) p.destPitch);
            }
            if (!p.permission.isEmpty())   yaml.set(base + ".permission", p.permission);
            if (!p.enterMessage.isEmpty()) yaml.set(base + ".enter-message", p.enterMessage);
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[ETCWorlds] Error guardando portals.yml: " + e.getMessage());
        }
    }

    /** Registra un nuevo portal. Devuelve false si ya existe uno con ese nombre. */
    public boolean add(CustomPortal portal) {
        if (portals.containsKey(portal.name.toLowerCase())) return false;
        portals.put(portal.name.toLowerCase(), portal);
        save();
        return true;
    }

    public boolean remove(String name) {
        boolean removed = portals.remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    /** Devuelve el portal cuyo bloque disparador coincide exactamente. */
    public CustomPortal getAt(int x, int y, int z, String worldName) {
        for (CustomPortal p : portals.values()) {
            if (p.triggerX == x && p.triggerY == y && p.triggerZ == z
                    && p.triggerWorld.equalsIgnoreCase(worldName)) {
                return p;
            }
        }
        return null;
    }

    public CustomPortal get(String name) {
        return portals.get(name.toLowerCase());
    }

    public Collection<CustomPortal> all() {
        return Collections.unmodifiableCollection(portals.values());
    }

    public int count() {
        return portals.size();
    }
}
