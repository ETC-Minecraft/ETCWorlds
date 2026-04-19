package com.etcmc.etcworlds.manager;

import com.etcmc.etcworlds.ETCWorlds;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Orquesta teleports a mundos potencialmente descargados:
 *   1. Carga el mundo si no estaba (async-friendly).
 *   2. Pre-carga chunks alrededor del destino vía getChunkAtAsync().
 *   3. Cuando todos están listos (o tras timeout), teleporta.
 *
 * Esto evita que el jugador aparezca en chunks vacíos y reduce el lag pico.
 */
public class LazyTeleportService {

    private final ETCWorlds plugin;

    public LazyTeleportService(ETCWorlds plugin) { this.plugin = plugin; }

    public void teleport(Player player, String worldName, Location destinationOrNull) {
        if (player == null || worldName == null) return;
        boolean enabled = plugin.getConfig().getBoolean("preload.enabled", true);
        int radius = plugin.getConfig().getInt("preload.radius", 4);
        long timeoutMs = plugin.getConfig().getLong("preload.timeout-ms", 5000);

        // Case-insensitive: resuelve el nombre canónico registrado
        String canonical = plugin.worlds().resolveWorldName(worldName);
        final String resolvedName = canonical != null ? canonical : worldName;

        // Busca el mundo ya cargado por nombre canónico o por el input original
        World world = Bukkit.getWorld(resolvedName);
        if (world == null) world = Bukkit.getWorld(worldName);
        if (world == null) {
            if (!plugin.worlds().isManaged(resolvedName)) {
                send(player, "unknown-world", "{world}", worldName);
                return;
            }
            // loadWorld llama a Bukkit.createWorld — debe correr en el hilo global (Folia)
            send(player, "world-loading", "{world}", worldName);
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                World w2 = plugin.worlds().loadWorld(resolvedName);
                if (w2 == null) {
                    player.getScheduler().run(plugin, t -> send(player, "unknown-world", "{world}", resolvedName), null);
                    return;
                }
                continueWithTeleport(player, w2, resolvedName, destinationOrNull, enabled, radius, timeoutMs);
            });
            return;
        }
        continueWithTeleport(player, world, resolvedName, destinationOrNull, enabled, radius, timeoutMs);
    }

    private void continueWithTeleport(Player player, World world, String worldName,
                                      Location destinationOrNull,
                                      boolean enabled, int radius, long timeoutMs) {
        Location dest0 = destinationOrNull;
        if (dest0 == null) {
            var rules = plugin.worlds().getRules(worldName);
            dest0 = rules != null ? rules.spawnLocation(world) : world.getSpawnLocation();
        }
        final Location dest = dest0;

        if (!enabled || radius <= 0) {
            doTeleport(player, dest);
            return;
        }

        long start = System.currentTimeMillis();
        CompletableFuture<Void> all = preloadChunks(world, dest, radius);
        all.orTimeout(timeoutMs, TimeUnit.MILLISECONDS).whenComplete((v, ex) -> {
            // Volver al hilo correcto del jugador (Folia)
            final Location finalDest = dest;
            final long elapsed = System.currentTimeMillis() - start;
            player.getScheduler().run(plugin, t -> {
                if (ex != null) send(player, "preload-timeout");
                doTeleport(player, finalDest);
                if (elapsed > 200)
                    plugin.getLogger().fine("Preload " + worldName + " tardó " + elapsed + "ms");
            }, null);
        });
    }

    private CompletableFuture<Void> preloadChunks(World w, Location center, int radius) {
        int cx = center.getBlockX() >> 4;
        int cz = center.getBlockZ() >> 4;
        java.util.List<CompletableFuture<Chunk>> futures = new java.util.ArrayList<>();
        for (int x = cx - radius; x <= cx + radius; x++)
            for (int z = cz - radius; z <= cz + radius; z++)
                futures.add(w.getChunkAtAsync(x, z, true));
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private void doTeleport(Player p, Location dest) {
        if (p == null || dest == null || !p.isOnline()) return;
        p.teleportAsync(dest).whenComplete((ok, err) -> {
            if (err != null)
                plugin.getLogger().log(Level.WARNING, "Error teletransportando a " + p.getName(), err);
            else if (Boolean.TRUE.equals(ok))
                send(p, "teleport-success", "{world}", dest.getWorld().getName());
        });
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void send(Player p, String key, String... pairs) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String msg = plugin.getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) msg = msg.replace(pairs[i], pairs[i + 1]);
        p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', prefix + msg));
    }
}
