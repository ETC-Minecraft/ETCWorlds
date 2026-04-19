package com.etcmc.etcworlds.hook;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.WorldRules;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Placeholders de ETCWorlds.
 *
 * Genéricos:
 *   %etcworlds_current%                 nombre del mundo actual del jugador
 *   %etcworlds_loaded_count%            mundos cargados en el server
 *   %etcworlds_managed_count%           mundos administrados por ETCWorlds
 *
 * Mundo actual del jugador (atajos):
 *   %etcworlds_world_displayname%       displayName del mundo donde está
 *                                       (si está vacío, devuelve el nombre del mundo)
 *   %etcworlds_world_name%              alias de %etcworlds_current%
 *   %etcworlds_world_template%          template del mundo actual
 *   %etcworlds_world_pvp%               true/false si pvp está activo
 *   %etcworlds_world_group%             grupo de inventario/xp/gm
 *
 * Mundo concreto:
 *   %etcworlds_displayname_<world>%     displayName de un mundo específico
 *   %etcworlds_template_<world>%        template
 *   %etcworlds_spawn_<world>%           "x,y,z"
 *   %etcworlds_player_count_<world>%    cuántos jugadores hay
 *   %etcworlds_pvp_<world>%             true/false
 *   %etcworlds_group_<world>%           grupo
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final ETCWorlds plugin;

    public PlaceholderHook(ETCWorlds plugin) { this.plugin = plugin; }

    @Override public @NotNull String getIdentifier() { return "etcworlds"; }
    @Override public @NotNull String getAuthor() { return "EmmanuelTC"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer p, @NotNull String params) {
        String key = params.toLowerCase();

        // ── Globales sin parámetro ─────────────────────────────────────────
        if (key.equals("current") || key.equals("world_name")) {
            return p != null && p.getPlayer() != null ? p.getPlayer().getWorld().getName() : "";
        }
        if (key.equals("loaded_count")) return String.valueOf(Bukkit.getWorlds().size());
        if (key.equals("managed_count")) return String.valueOf(plugin.worlds().getManagedNames().size());

        // ── Atajos del mundo actual del jugador ────────────────────────────
        Player online = p != null ? p.getPlayer() : null;
        if (online != null) {
            String currentName = online.getWorld().getName();
            if (key.equals("world_displayname") || key.equals("world_display_name")) {
                return resolveDisplayName(currentName);
            }
            if (key.equals("world_template")) {
                WorldRules r = plugin.worlds().getRules(currentName);
                return r != null ? r.template.name() : "";
            }
            if (key.equals("world_pvp")) {
                WorldRules r = plugin.worlds().getRules(currentName);
                return String.valueOf(r != null && r.pvp);
            }
            if (key.equals("world_group")) {
                WorldRules r = plugin.worlds().getRules(currentName);
                return r != null ? r.worldGroup : "";
            }
        }

        // ── Con parámetro: <prefijo>_<world> ───────────────────────────────
        if (key.startsWith("player_count_")) {
            String wn = params.substring("player_count_".length());
            World w = Bukkit.getWorld(wn);
            return w != null ? String.valueOf(w.getPlayers().size()) : "0";
        }
        if (key.startsWith("displayname_")) {
            return resolveDisplayName(params.substring("displayname_".length()));
        }
        if (key.startsWith("display_name_")) {
            return resolveDisplayName(params.substring("display_name_".length()));
        }
        if (key.startsWith("template_")) {
            WorldRules r = plugin.worlds().getRules(params.substring("template_".length()));
            return r != null ? r.template.name() : "";
        }
        if (key.startsWith("spawn_")) {
            WorldRules r = plugin.worlds().getRules(params.substring("spawn_".length()));
            if (r == null) return "";
            return ((int) r.spawnX) + "," + ((int) r.spawnY) + "," + ((int) r.spawnZ);
        }
        if (key.startsWith("pvp_")) {
            WorldRules r = plugin.worlds().getRules(params.substring("pvp_".length()));
            return String.valueOf(r != null && r.pvp);
        }
        if (key.startsWith("group_")) {
            WorldRules r = plugin.worlds().getRules(params.substring("group_".length()));
            return r != null ? r.worldGroup : "";
        }

        return null;
    }

    private String resolveDisplayName(String worldName) {
        WorldRules r = plugin.worlds().getRules(worldName);
        if (r != null && !r.displayName.isEmpty()) return r.displayName;
        return worldName;
    }
}
