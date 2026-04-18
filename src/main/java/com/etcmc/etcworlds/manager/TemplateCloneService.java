package com.etcmc.etcworlds.manager;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.WorldRules;
import com.etcmc.etcworlds.util.WorldFiles;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;

/**
 * Clona un mundo plantilla (isTemplate=true) bajo otro nombre, listo para usar.
 * No requiere generator: se usa la copia exacta de la carpeta del mundo origen.
 */
public class TemplateCloneService {

    private final ETCWorlds plugin;

    public TemplateCloneService(ETCWorlds plugin) { this.plugin = plugin; }

    public boolean cloneTemplate(String template, String newName) throws IOException {
        WorldRules tr = plugin.worlds().getRules(template);
        if (tr == null) throw new IOException("Plantilla no registrada: " + template);
        if (!tr.isTemplate) throw new IOException("El mundo no está marcado como plantilla (use /etcworlds set " + template + " template true).");
        if (plugin.worlds().isManaged(newName)) throw new IOException("Ya existe un mundo con ese nombre.");

        // Si la plantilla está cargada, salvar antes de copiar para que el snapshot esté completo
        World tw = Bukkit.getWorld(template);
        if (tw != null) tw.save();

        File from = plugin.worlds().worldDirOf(template);
        String dirName = plugin.getConfig().getString("worlds-folder", "mundos");
        String relative = dirName + "/" + newName;
        File to = new File(Bukkit.getWorldContainer(), relative);
        if (to.exists()) throw new IOException("Carpeta destino ya existe.");
        WorldFiles.copyDir(from, to);
        new File(to, "uid.dat").delete();
        new File(to, "session.lock").delete();

        // Reglas: copiar las del template pero "destemplatizar"
        WorldRules nr = new WorldRules();
        nr.name = newName;
        nr.template = tr.template;
        nr.environment = tr.environment;
        nr.ambient = tr.ambient;
        nr.gamemode = tr.gamemode;
        nr.pvp = tr.pvp;
        nr.worldGroup = tr.worldGroup;
        nr.spawnX = tr.spawnX; nr.spawnY = tr.spawnY; nr.spawnZ = tr.spawnZ;
        nr.linkedNether = tr.linkedNether;
        nr.linkedEnd = tr.linkedEnd;
        nr.isTemplate = false;
        nr.perPlayerInstance = false;

        File rf = new File(to, "etcworlds.yml");
        org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
        nr.writeTo(y);
        y.save(rf);

        plugin.worlds().importExisting(newName, relative);
        plugin.worlds().loadRegistry();
        return true;
    }
}
