package com.etcmc.etcworlds.hook;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.model.WorldRules;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/**
 * Placeholders disponibles:
 *   %etcworlds_current%               nombre del mundo actual del jugador
 *   %etcworlds_loaded_count%          mundos cargados
 *   %etcworlds_managed_count%         mundos administrados
 *   %etcworlds_player_count_<world>%  jugadores en un mundo concreto
 *   %etcworlds_template_<world>%      template del mundo
 *   %etcworlds_spawn_<world>%         coordenadas spawn x,y,z
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
        if (params.equalsIgnoreCase("current"))
            return p != null && p.getPlayer() != null ? p.getPlayer().getWorld().getName() : "";
        if (params.equalsIgnoreCase("loaded_count")) return String.valueOf(Bukkit.getWorlds().size());
        if (params.equalsIgnoreCase("managed_count")) return String.valueOf(plugin.worlds().getManagedNames().size());

        String[] parts = params.split("_", 2);
        if (parts.length == 2) {
            String key = parts[0].toLowerCase();
            String name = parts[1];
            switch (key) {
                case "player" -> {
                    if (name.startsWith("count_")) name = name.substring(6);
                    World w = Bukkit.getWorld(name);
                    return w != null ? String.valueOf(w.getPlayers().size()) : "0";
                }
                case "template" -> {
                    WorldRules r = plugin.worlds().getRules(name);
                    return r != null ? r.template.name() : "";
                }
                case "spawn" -> {
                    WorldRules r = plugin.worlds().getRules(name);
                    if (r == null) return "";
                    return ((int) r.spawnX) + "," + ((int) r.spawnY) + "," + ((int) r.spawnZ);
                }
            }
            // %etcworlds_player_count_<world>%
            if (params.startsWith("player_count_")) {
                String wn = params.substring("player_count_".length());
                World w = Bukkit.getWorld(wn);
                return w != null ? String.valueOf(w.getPlayers().size()) : "0";
            }
        }
        return null;
    }
}
