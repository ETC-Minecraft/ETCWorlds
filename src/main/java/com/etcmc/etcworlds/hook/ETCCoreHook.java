package com.etcmc.etcworlds.hook;

import com.etcmc.etcworlds.ETCWorlds;
import org.bukkit.Bukkit;

/**
 * Bridge opcional con ETCCore. Si ETCCore está cargado, expone:
 *   - Acción extendida para CommandBuilder/MenuBuilder: el plugin ETCCore puede
 *     llamar al servicio LazyTeleportService desde sus comandos YAML usando el comando
 *     vanilla "/world &lt;nombre&gt;" gracias a [run] (no requiere bridge custom).
 *   - Lectura del mundo actual del jugador para variables: ETCCore ya expone
 *     {var:player.world} en sus placeholders.
 *
 * Si necesitas integraciones más profundas (ej. publicar el spawn de cada mundo
 * a un PlayerDataManager de ETCCore al cambiar de mundo), se puede añadir aquí
 * con reflexión sin romper compilación si ETCCore no está presente.
 */
public class ETCCoreHook {

    private final ETCWorlds plugin;
    private boolean enabled;

    public ETCCoreHook(ETCWorlds plugin) { this.plugin = plugin; }

    public void register() {
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("ETCCore");
        if (enabled) plugin.getLogger().info("ETCCore detectado: integración habilitada.");
        else plugin.getLogger().info("ETCCore no presente: integración omitida.");
    }

    public boolean isEnabled() { return enabled; }
}
