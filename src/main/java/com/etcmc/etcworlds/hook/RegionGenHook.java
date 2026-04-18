package com.etcmc.etcworlds.hook;

import com.etcmc.etcworlds.ETCWorlds;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;

/**
 * Bridge opcional con ETCRegionGenerator. Si está disponible, expone un método
 * helper para pre-generar X chunks de un mundo recién creado vía comando consola.
 *
 * No requiere classpath de RegionGenerator: se usa el comando registrado.
 */
public class RegionGenHook {

    private final ETCWorlds plugin;
    private boolean enabled;

    public RegionGenHook(ETCWorlds plugin) { this.plugin = plugin; }

    public void detect() {
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("ETCRegionGenerator");
        if (enabled) plugin.getLogger().info("ETCRegionGenerator detectado: se podrá pre-generar mundos.");
    }

    public boolean isEnabled() { return enabled; }

    /** Lanza /pregenerate &lt;world&gt; &lt;radius&gt; (asumido como API pública del plugin externo). */
    public boolean pregenerate(String worldName, int radius) {
        if (!enabled) return false;
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        return Bukkit.dispatchCommand(console, "pregenerate " + worldName + " " + radius);
    }
}
