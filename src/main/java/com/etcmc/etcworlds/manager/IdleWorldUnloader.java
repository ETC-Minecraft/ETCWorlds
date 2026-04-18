package com.etcmc.etcworlds.manager;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.WorldRules;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Descarga automáticamente mundos vacíos tras X segundos para liberar RAM/TPS.
 * Respeta keep-loaded global y per-world isTemplate / keepLoaded.
 */
public class IdleWorldUnloader {

    private final ETCWorlds plugin;
    private ScheduledTask task;
    private final Map<String, Long> emptySince = new HashMap<>();

    public IdleWorldUnloader(ETCWorlds plugin) { this.plugin = plugin; }

    public void start() {
        if (!plugin.getConfig().getBoolean("idle-unload.enabled", true)) return;
        long intervalSec = plugin.getConfig().getLong("idle-unload.check-interval-seconds", 60);
        long ticks = Math.max(20L, intervalSec * 20L);
        this.task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> tick(), ticks, ticks);
    }

    public void stop() { if (task != null) task.cancel(); }

    private void tick() {
        long unloadAfter = TimeUnit.SECONDS.toMillis(
                plugin.getConfig().getLong("idle-unload.unload-after-seconds", 600));
        boolean save = plugin.getConfig().getBoolean("idle-unload.save-on-unload", true);
        List<String> keep = plugin.getConfig().getStringList("keep-loaded");

        long now = System.currentTimeMillis();
        for (World w : new java.util.ArrayList<>(Bukkit.getWorlds())) {
            String name = w.getName();
            if (keep.contains(name)) { emptySince.remove(name); continue; }
            WorldRules r = plugin.worlds().getRules(name);
            if (r != null && (r.keepLoaded || r.isTemplate)) { emptySince.remove(name); continue; }
            if (!w.getPlayers().isEmpty()) { emptySince.remove(name); continue; }
            // No descargar el primer mundo (default)
            if (Bukkit.getWorlds().get(0).equals(w)) continue;

            Long since = emptySince.get(name);
            if (since == null) { emptySince.put(name, now); continue; }
            if (now - since < unloadAfter) continue;

            try {
                if (Bukkit.unloadWorld(w, save)) {
                    plugin.getLogger().info("Mundo descargado por inactividad: " + name);
                    emptySince.remove(name);
                }
            } catch (UnsupportedOperationException foliaEx) {
                // Folia no permite descarga dinámica — resetear el timer para no loggear cada ciclo
                emptySince.remove(name);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Error descargando " + name, ex);
                emptySince.remove(name);
            }
        }
    }
}
