package com.etcmc.etcworlds.manager;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.WorldRules;
import com.etcmc.etcworlds.util.WorldFiles;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Snapshots automáticos rotativos de mundos administrados.
 * Cada snapshot es un .zip con el contenido íntegro de la carpeta del mundo.
 */
public class BackupManager {

    private final ETCWorlds plugin;
    private ScheduledTask task;

    public BackupManager(ETCWorlds plugin) { this.plugin = plugin; }

    public void start() {
        if (!plugin.getConfig().getBoolean("backups.enabled", false)) return;
        long hours = Math.max(1, plugin.getConfig().getLong("backups.interval-hours", 24));
        long ticks = TimeUnit.HOURS.toSeconds(hours) * 20L;
        // Spread: primer run en 5 min para no chocar con startup
        this.task = Bukkit.getAsyncScheduler().runAtFixedRate(plugin,
                t -> runAllBackups(), 5, hours * 3600, TimeUnit.SECONDS);
    }

    public void stop() { if (task != null) task.cancel(); }

    public void runAllBackups() {
        List<String> exclude = plugin.getConfig().getStringList("backups.exclude");
        for (String name : plugin.worlds().getManagedNames()) {
            if (exclude.contains(name)) continue;
            WorldRules r = plugin.worlds().getRules(name);
            if (r != null && !r.backupEnabled) continue;
            try { backupWorld(name); }
            catch (Exception ex) { plugin.getLogger().log(Level.WARNING, "Backup falló: " + name, ex); }
        }
    }

    public File backupWorld(String name) throws Exception {
        File worldDir = plugin.worlds().worldDirOf(name);
        if (worldDir == null || !worldDir.exists())
            throw new IllegalStateException("Carpeta no existe para " + name);

        boolean saveBefore = plugin.getConfig().getBoolean("backups.save-before-backup", true);
        if (saveBefore) {
            World w = Bukkit.getWorld(name);
            if (w != null) Bukkit.getGlobalRegionScheduler().run(plugin, t -> w.save());
        }

        String folder = plugin.getConfig().getString("backups.folder", "mundos-backups");
        File backupRoot = new File(Bukkit.getWorldContainer(), folder);
        if (!backupRoot.exists()) backupRoot.mkdirs();

        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File outZip = new File(backupRoot, name + "_" + stamp + ".zip");
        WorldFiles.zip(worldDir, outZip);
        rotate(backupRoot, name);
        plugin.getLogger().info("Backup creado: " + outZip.getName() +
                " (" + WorldFiles.formatSize(outZip.length()) + ")");
        return outZip;
    }

    private void rotate(File backupRoot, String worldName) {
        int retain = Math.max(1, plugin.getConfig().getInt("backups.retain", 7));
        File[] files = backupRoot.listFiles((dir, name) ->
                name.startsWith(worldName + "_") && name.endsWith(".zip"));
        if (files == null || files.length <= retain) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int toDelete = files.length - retain;
        for (int i = 0; i < toDelete; i++) {
            if (files[i].delete())
                plugin.getLogger().info("Backup rotado eliminado: " + files[i].getName());
        }
    }
}
