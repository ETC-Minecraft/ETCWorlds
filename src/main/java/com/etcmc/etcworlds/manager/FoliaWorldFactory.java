package com.etcmc.etcworlds.manager;

import com.etcmc.etcworlds.ETCWorlds;
import org.bukkit.World;
import org.bukkit.WorldCreator;

/**
 * Detecta si el servidor corre Folia e intenta crear mundos via reflection
 * sin depender de NMS importado en compile-time.
 *
 * En Folia, Bukkit.createWorld() lanza UnsupportedOperationException.
 * WorldsManager ya captura esa excepcion como fallback; este factory actua
 * como punto de extension para implementaciones reflection en tiempo real.
 */
public final class FoliaWorldFactory {

    private static final boolean RUNNING_FOLIA;

    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {
            // No es Folia
        }
        RUNNING_FOLIA = folia;
    }

    private FoliaWorldFactory() {}

    public static boolean isFolia() {
        return RUNNING_FOLIA;
    }

    /**
     * En Folia, la creacion de mundos en tiempo real no esta soportada sin NMS.
     * Retorna null para que WorldsManager registre el mundo y lo cargue al reiniciar.
     */
    public static World createWorld(ETCWorlds plugin, WorldCreator creator) {
        plugin.getLogger().warning(
            "[ETCWorlds] Folia detectado - creacion de mundos en tiempo real no soportada. " +
            "El mundo quedara registrado. Reinicia el servidor para que cargue automaticamente.");
        return null;
    }
}
