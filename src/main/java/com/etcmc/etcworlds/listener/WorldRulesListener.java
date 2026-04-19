package com.etcmc.etcworlds.listener;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.WorldRules;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Locale;

/**
 * Aplica las reglas por-mundo a eventos del jugador:
 * gamemode forzado, fly forzado, build/break, fall-damage, hunger,
 * y filtro de comandos por mundo.
 */
public class WorldRulesListener implements Listener {

    private final ETCWorlds plugin;

    public WorldRulesListener(ETCWorlds plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        applyRulesTo(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangeWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        // Mensaje de salida del mundo anterior
        WorldRules old = plugin.worlds().getRules(e.getFrom().getName());
        if (old != null && !old.exitMessage.isEmpty())
            p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    old.exitMessage.replace("{world}", e.getFrom().getName())));
        // Aplicar reglas en el mismo tick (gamemode si aplica)
        applyRulesTo(p);
        // Reaplicar 1 tick despues para sobrevivir al WorldGroupsListener (MONITOR),
        // que carga el snapshot del grupo y llama setGameMode -> resetea allowFlight.
        try {
            p.getScheduler().runDelayed(plugin, t -> applyFlyAndHunger(p), null, 2L);
        } catch (Throwable ignored) {
            // Fallback no-Folia
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> applyFlyAndHunger(p), 2L);
        }
        // Mensaje de entrada al nuevo mundo
        WorldRules next = plugin.worlds().getRules(p.getWorld().getName());
        if (next != null && !next.enterMessage.isEmpty())
            p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    next.enterMessage.replace("{world}", p.getWorld().getName())));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (e.isBedSpawn() || e.isAnchorSpawn()) return;
        WorldRules r = plugin.worlds().getRules(e.getPlayer().getWorld().getName());
        if (r == null) return;
        Location spawn = r.spawnLocation(e.getPlayer().getWorld());
        if (spawn != null) e.setRespawnLocation(spawn);
    }

    private void applyRulesTo(Player p) {
        WorldRules r = plugin.worlds().getRules(p.getWorld().getName());
        if (r == null) return;
        if (r.gamemode != null && p.getGameMode() != r.gamemode
                && !p.hasPermission("etcworlds.bypass.gamemode"))
            p.setGameMode(r.gamemode);
        applyFlyAndHunger(p);
    }

    /**
     * Aplica fly y hunger del mundo actual. Se llama tambien con delay desde
     * onChangeWorld para sobrevivir al setGameMode() que hace WorldGroupsListener
     * a prioridad MONITOR (que resetea allowFlight=false).
     */
    private void applyFlyAndHunger(Player p) {
        if (!p.isOnline()) return;
        WorldRules r = plugin.worlds().getRules(p.getWorld().getName());
        if (r == null) return;
        GameMode gm = p.getGameMode();
        boolean alwaysFlying = gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;
        if (r.fly) {
            if (!p.getAllowFlight()) p.setAllowFlight(true);
        } else if (!alwaysFlying) {
            if (p.getAllowFlight()) p.setAllowFlight(false);
            if (p.isFlying()) p.setFlying(false);
        }
        // Hunger auto-fill al entrar si hunger=false
        if (!r.hunger) {
            p.setFoodLevel(20);
            p.setSaturation(20f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        String world = p.getWorld().getName();
        if (denyPocketBuild(p, world)) { e.setCancelled(true); return; }
        if (isPocketTrusted(p, world)) return; // owner/users del PW saltan reglas de mundo
        WorldRules r = plugin.worlds().getRules(world);
        if (r != null && !r.buildEnabled && !p.hasPermission("etcworlds.bypass.access"))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        String world = p.getWorld().getName();
        if (denyPocketBuild(p, world)) { e.setCancelled(true); return; }
        if (isPocketTrusted(p, world)) return;
        WorldRules r = plugin.worlds().getRules(world);
        if (r != null && !r.buildEnabled && !p.hasPermission("etcworlds.bypass.access"))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        Player p = e.getPlayer();
        if (denyPocketBuild(p, p.getWorld().getName())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        Player p = e.getPlayer();
        if (denyPocketBuild(p, p.getWorld().getName())) e.setCancelled(true);
    }

    /** Bloquea interacciones (puertas, cofres, palancas, botones, etc.) en pocketworlds para no-invitados,
     *  o globalmente si interactEnabled=false en mundos no-pocket. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;
        Player p = e.getPlayer();
        String world = p.getWorld().getName();
        org.bukkit.Material m = e.getClickedBlock().getType();
        if (!isInteractiveBlock(m)) return;

        // Pocketworld: bloquear si no es trusted
        if (plugin.pocketWorlds() != null && plugin.pocketWorlds().isPocketWorld(world)) {
            if (!p.hasPermission("etcworlds.pw.bypass") && !p.hasPermission("etcworlds.bypass.access") && !p.isOp()
                    && !plugin.pocketWorlds().canBuild(p.getUniqueId(), world)) {
                e.setCancelled(true);
                return;
            }
            // Owner/users del PW saltan la regla interactEnabled del mundo.
            if (isPocketTrusted(p, world)) return;
        }
        // Mundos no-pocket: usar regla global
        WorldRules r = plugin.worlds().getRules(world);
        if (r != null && !r.interactEnabled && !p.hasPermission("etcworlds.bypass.access"))
            e.setCancelled(true);
    }

    private static boolean isInteractiveBlock(org.bukkit.Material m) {
        if (m == null) return false;
        String n = m.name();
        if (m == org.bukkit.Material.CRAFTING_TABLE || m == org.bukkit.Material.ENCHANTING_TABLE
                || m == org.bukkit.Material.ANVIL || m == org.bukkit.Material.CHIPPED_ANVIL
                || m == org.bukkit.Material.DAMAGED_ANVIL || m == org.bukkit.Material.GRINDSTONE
                || m == org.bukkit.Material.LOOM || m == org.bukkit.Material.STONECUTTER
                || m == org.bukkit.Material.SMITHING_TABLE || m == org.bukkit.Material.CARTOGRAPHY_TABLE
                || m == org.bukkit.Material.LEVER || m == org.bukkit.Material.REPEATER
                || m == org.bukkit.Material.COMPARATOR || m == org.bukkit.Material.NOTE_BLOCK
                || m == org.bukkit.Material.JUKEBOX || m == org.bukkit.Material.LECTERN
                || m == org.bukkit.Material.BEACON || m == org.bukkit.Material.BELL
                || m == org.bukkit.Material.CAKE || m == org.bukkit.Material.RESPAWN_ANCHOR
                || m == org.bukkit.Material.COMPOSTER) return true;
        return n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR") || n.endsWith("_BUTTON")
                || n.endsWith("_FENCE_GATE") || n.endsWith("_SHULKER_BOX")
                || n.endsWith("_BED") || n.endsWith("_SIGN") || n.endsWith("_HANGING_SIGN")
                || n.contains("CHEST") || n.contains("BARREL") || n.contains("FURNACE")
                || n.contains("HOPPER") || n.contains("DISPENSER") || n.contains("DROPPER")
                || n.contains("BREWING_STAND") || n.contains("CANDLE");
    }

    /**
     * Devuelve true si el jugador NO puede construir en este pocketworld.
     * Para mundos no-pocket retorna false (no aplica el bloqueo).
     * Bypass: ops o permiso etcworlds.pw.bypass / etcworlds.bypass.access.
     */
    private boolean denyPocketBuild(Player p, String world) {
        if (plugin.pocketWorlds() == null) return false;
        if (!plugin.pocketWorlds().isPocketWorld(world)) return false;
        if (p.hasPermission("etcworlds.pw.bypass") || p.hasPermission("etcworlds.bypass.access") || p.isOp())
            return false;
        if (plugin.pocketWorlds().canBuild(p.getUniqueId(), world)) return false;
        // mensaje suave para evitar spam (1/seg) — uso simple via metadata
        long now = System.currentTimeMillis();
        Long last = lastDenyMsg.get(p.getUniqueId());
        if (last == null || now - last > 1500) {
            p.sendMessage(ChatColor.RED + "No tienes permiso para construir aqui. Pide al dueno con /pw add <tu>.");
            lastDenyMsg.put(p.getUniqueId(), now);
        }
        return true;
    }

    private final java.util.Map<java.util.UUID, Long> lastDenyMsg = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Devuelve true si el mundo es un pocketworld y el jugador es DUENO o esta en
     * la lista de "users" (anadido con /pw useradd). Estos jugadores saltan las
     * reglas de mundo para build/interact/buckets.
     * Para mundos no-pocket retorna false.
     */
    private boolean isPocketTrusted(Player p, String world) {
        if (plugin.pocketWorlds() == null) return false;
        if (!plugin.pocketWorlds().isPocketWorld(world)) return false;
        java.util.UUID owner = plugin.pocketWorlds().getOwnerOf(world);
        if (owner == null) return false;
        return plugin.pocketWorlds().isUser(owner, p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(e.getEntity() instanceof Player p)) return;
        WorldRules r = plugin.worlds().getRules(p.getWorld().getName());
        if (r != null && !r.fallDamage) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        WorldRules r = plugin.worlds().getRules(p.getWorld().getName());
        if (r != null && !r.hunger) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        WorldRules r = plugin.worlds().getRules(e.getPlayer().getWorld().getName());
        if (r == null) return;
        String raw = e.getMessage().toLowerCase(Locale.ROOT);
        String cmd = raw.startsWith("/") ? raw.substring(1).split(" ")[0] : raw.split(" ")[0];
        // Si hay whitelist no vacía y no contiene el comando -> bloquear
        if (!r.commandWhitelist.isEmpty() && r.commandWhitelist.stream().noneMatch(c -> c.equalsIgnoreCase(cmd))) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cEse comando no está permitido en este mundo.");
            return;
        }
        if (r.commandBlacklist.stream().anyMatch(c -> c.equalsIgnoreCase(cmd))) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cEse comando está bloqueado en este mundo.");
        }
    }
}
