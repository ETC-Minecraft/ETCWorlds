package com.etcmc.etcworlds.manager;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.WorldRules;
import com.etcmc.etcworlds.util.WorldFiles;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Gestiona instancias por jugador de mundos plantilla.
 * Cuando un jugador entra a una plantilla con perPlayerInstance=true, se clona
 * la carpeta de la plantilla y se monta como mundo independiente para él.
 *
 * El mundo instancia se nombra: instance_&lt;templateName&gt;_&lt;uuidShort&gt;.
 * Se descarga al salir el jugador (vía idle-unload, no inmediato).
 */
public class InstanceManager {

    private final ETCWorlds plugin;

    public InstanceManager(ETCWorlds plugin) { this.plugin = plugin; }

    public String instanceWorldName(String template, UUID uuid) {
        String s = uuid.toString().replace("-", "").substring(0, 8);
        return "instance_" + template.toLowerCase() + "_" + s;
    }

    /** Carga (o crea) la instancia del jugador para una plantilla. Devuelve el mundo listo. */
    public World ensureInstance(Player p, String template) throws IllegalStateException {
        WorldRules tmplRules = plugin.worlds().getRules(template);
        if (tmplRules == null) throw new IllegalStateException("Plantilla no registrada: " + template);
        if (!tmplRules.isTemplate)
            throw new IllegalStateException("El mundo " + template + " no está marcado como plantilla.");

        String instanceName = instanceWorldName(template, p.getUniqueId());
        World existing = Bukkit.getWorld(instanceName);
        if (existing != null) return existing;

        // Si ya está registrado (existe en disco), simplemente cargar
        if (plugin.worlds().isManaged(instanceName)) return plugin.worlds().loadWorld(instanceName);

        // Clonar carpeta de la plantilla
        File tmplDir = plugin.worlds().worldDirOf(template);
        if (tmplDir == null || !tmplDir.exists())
            throw new IllegalStateException("Carpeta plantilla no existe: " + template);

        String dirName = plugin.getConfig().getString("worlds-folder", "mundos");
        String relative = dirName + "/" + instanceName;
        File destDir = new File(Bukkit.getWorldContainer(), relative);

        try {
            WorldFiles.copyDir(tmplDir, destDir);
            // Borrar uid.dat para que no choque con la plantilla
            new File(destDir, "uid.dat").delete();
            // Borrar session.lock por si acaso
            new File(destDir, "session.lock").delete();
        } catch (IOException ex) {
            throw new IllegalStateException("Error clonando plantilla: " + ex.getMessage());
        }

        // Registrar y cargar
        WorldRules ir = new WorldRules();
        ir.name = instanceName;
        ir.template = tmplRules.template;
        ir.environment = tmplRules.environment;
        ir.ambient = tmplRules.ambient;
        ir.gamemode = tmplRules.gamemode;
        ir.pvp = false;            // las instancias suelen ser personales
        ir.publicAccess = false;
        ir.whitelist.add(p.getUniqueId().toString());
        ir.worldGroup = tmplRules.worldGroup;
        ir.spawnX = tmplRules.spawnX; ir.spawnY = tmplRules.spawnY; ir.spawnZ = tmplRules.spawnZ;
        ir.linkedNether = tmplRules.linkedNether;
        ir.linkedEnd = tmplRules.linkedEnd;
        ir.keepLoaded = false;
        ir.isTemplate = false;
        ir.perPlayerInstance = false; // nunca recursivo

        plugin.worlds().importExisting(instanceName, relative); // registra carpeta
        // Sobrescribimos las reglas con las personalizadas y cargamos:
        plugin.worlds().getRules(instanceName); // ensure entry exists
        // Reemplazo manual: re-asignar reglas vía field (truco: guardarlas y recargar el mundo)
        var rulesMap = plugin.worlds();
        // No tenemos setter público; guardamos el archivo y recargamos
        File rulesFile = new File(destDir, "etcworlds.yml");
        org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
        ir.writeTo(y);
        try { y.save(rulesFile); } catch (IOException ignored) {}
        plugin.worlds().loadRegistry(); // recarga reglas desde disco

        World w = plugin.worlds().loadWorld(instanceName);
        if (w == null) throw new IllegalStateException("No se pudo cargar la instancia.");
        return w;
    }
}
