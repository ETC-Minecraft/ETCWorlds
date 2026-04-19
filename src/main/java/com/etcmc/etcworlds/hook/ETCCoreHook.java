package com.etcmc.etcworlds.hook;

import com.etcmc.etcworlds.ETCWorlds;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Bridge opcional con ETCCore.
 *
 * Cuando ETCCore está instalado junto a ETCWorlds, ETCWorlds toma el control
 * de /spawn y /lobby sobreescribiéndolos en el CommandMap de Bukkit. Así:
 *   - /spawn  → spawn del mundo actual (ETCWorlds per-world spawn)
 *   - /lobby  → mundo lobby configurado en ETCWorlds config.yml
 *
 * ETCCore sigue disponible como /etccore:spawn y /etccore:lobby para admins.
 *
 * Las acciones [WORLD], [SPAWN] y [LOBBY] ya fueron añadidas directamente a
 * CustomCommand.processAction() en ETCCore para usarse en CommandBuilder/MenuBuilder.
 */
public class ETCCoreHook {

    private final ETCWorlds plugin;
    private boolean enabled;

    public ETCCoreHook(ETCWorlds plugin) { this.plugin = plugin; }

    public void register() {
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("ETCCore");
        if (enabled) {
            plugin.getLogger().info("ETCCore detectado: ETCWorlds tomará control de /spawn y /lobby.");
            overrideSpawnLobby();
        } else {
            plugin.getLogger().info("ETCCore no presente: integración omitida.");
        }
    }

    /**
     * Sobreescribe /spawn y /lobby en el CommandMap de Bukkit para que
     * respondan al executor de ETCWorlds en lugar del de ETCCore.
     * ETCCore carga primero (es soft-depend), por lo que sus comandos ganan
     * prioridad por defecto — este método la revierte.
     */
    private void overrideSpawnLobby() {
        try {
            // Acceder al CommandMap via reflexión (compatible con todas las versiones de Paper/Folia)
            Method getMap = Bukkit.getServer().getClass().getMethod("getCommandMap");
            Object commandMap = getMap.invoke(Bukkit.getServer());
            @SuppressWarnings("unchecked")
            Map<String, Command> known = (Map<String, Command>)
                    commandMap.getClass().getMethod("getKnownCommands").invoke(commandMap);

            org.bukkit.command.PluginCommand spawn = plugin.getCommand("spawn");
            org.bukkit.command.PluginCommand lobby = plugin.getCommand("lobby");

            if (spawn != null) {
                known.put("spawn", spawn);
                known.put("etccore:spawn", spawn); // también el prefijado
                plugin.getLogger().info("  \u2192 /spawn ahora gestionado por ETCWorlds.");
            }
            if (lobby != null) {
                known.put("lobby", lobby);
                known.put("etccore:lobby", lobby);
                plugin.getLogger().info("  \u2192 /lobby ahora gestionado por ETCWorlds.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[ETCWorlds] No se pudo sobreescribir /spawn y /lobby de ETCCore: " + e.getMessage());
        }
    }

    public boolean isEnabled() { return enabled; }
}

