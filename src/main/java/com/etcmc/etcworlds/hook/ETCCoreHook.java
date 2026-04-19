package com.etcmc.etcworlds.hook;

import com.etcmc.etcworlds.ETCWorlds;
import com.etcmc.etcworlds.command.WorldTpCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Bridge opcional con ETCCore.
 * Si ETCCore está cargado, toma el control de /spawn y /lobby usando el CommandMap
 * para que ETCWorlds pueda teletransportar al spawn del mundo actual o al lobby.
 */
public class ETCCoreHook {

    private final ETCWorlds plugin;
    private boolean enabled;

    public ETCCoreHook(ETCWorlds plugin) { this.plugin = plugin; }

    public void register() {
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("ETCCore");
        if (enabled) {
            plugin.getLogger().info("[ETCWorlds] ETCCore detectado: tomando control de /spawn y /lobby.");
            overrideSpawnLobby();
        } else {
            plugin.getLogger().info("[ETCWorlds] ETCCore no presente: integracion omitida.");
        }
    }

    @SuppressWarnings("unchecked")
    private void overrideSpawnLobby() {
        try {
            // Obtener el CommandMap via reflection
            Field cmdMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            cmdMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) cmdMapField.get(Bukkit.getServer());

            // Obtener el mapa interno knownCommands
            Field knownField = commandMap.getClass().getDeclaredField("knownCommands");
            knownField.setAccessible(true);
            Map<String, Command> known = (Map<String, Command>) knownField.get(commandMap);

            WorldTpCommand tpCmd = new WorldTpCommand(plugin);
            PluginCommand spawnCmd = plugin.getCommand("spawn");
            PluginCommand lobbyCmd = plugin.getCommand("lobby");

            if (spawnCmd != null) {
                known.put("spawn", spawnCmd);
                known.put("etccore:spawn", spawnCmd);
                plugin.getLogger().info("[ETCWorlds] /spawn tomado de ETCCore.");
            }
            if (lobbyCmd != null) {
                known.put("lobby", lobbyCmd);
                known.put("etccore:lobby", lobbyCmd);
                plugin.getLogger().info("[ETCWorlds] /lobby tomado de ETCCore.");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("[ETCWorlds] No se pudo tomar /spawn y /lobby: " + ex.getMessage());
        }
    }

    public boolean isEnabled() { return enabled; }
}
