package com.etcmc.etcworlds;

import com.etcmc.etcworlds.command.PocketWorldCommand;
import com.etcmc.etcworlds.command.WorldTpCommand;
import com.etcmc.etcworlds.command.WorldsCommand;
import com.etcmc.etcworlds.gui.PocketRulesGUI;
import com.etcmc.etcworlds.gui.WorldsGUI;
import com.etcmc.etcworlds.hook.ETCCoreHook;
import com.etcmc.etcworlds.hook.PlaceholderHook;
import com.etcmc.etcworlds.hook.RegionGenHook;
import com.etcmc.etcworlds.listener.CustomPortalListener;
import com.etcmc.etcworlds.listener.OneblockListener;
import com.etcmc.etcworlds.listener.PortalLinkListener;
import com.etcmc.etcworlds.listener.WorldAccessListener;
import com.etcmc.etcworlds.listener.WorldGroupsListener;
import com.etcmc.etcworlds.listener.WorldRulesListener;
import com.etcmc.etcworlds.manager.BackupManager;
import com.etcmc.etcworlds.manager.CustomPortalManager;
import com.etcmc.etcworlds.manager.IdleWorldUnloader;
import com.etcmc.etcworlds.manager.InstanceManager;
import com.etcmc.etcworlds.manager.LazyTeleportService;
import com.etcmc.etcworlds.manager.PocketWorldManager;
import com.etcmc.etcworlds.manager.TemplateCloneService;
import com.etcmc.etcworlds.manager.WorldGroupsManager;
import com.etcmc.etcworlds.manager.WorldsManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public final class ETCWorlds extends JavaPlugin {

    private static ETCWorlds instance;

    private WorldsManager worldsManager;
    private LazyTeleportService lazyTeleportService;
    private IdleWorldUnloader idleWorldUnloader;
    private BackupManager backupManager;
    private ETCCoreHook etcCoreHook;
    private RegionGenHook regionGenHook;
    private WorldGroupsManager worldGroupsManager;
    private InstanceManager instanceManager;
    private TemplateCloneService templateCloneService;
    private WorldsGUI worldsGUI;
    private PocketRulesGUI pocketRulesGUI;
    private CustomPortalManager customPortalManager;
    private PocketWorldManager pocketWorldManager;

    public static ETCWorlds get() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
        saveDefaultConfig();
        // WorldsManager debe existir en onLoad para responder a getDefaultWorldGenerator()
        // que Bukkit invoca durante el arranque al cargar mundos vanilla declarados en bukkit.yml.
        this.worldsManager = new WorldsManager(this);
        this.worldsManager.loadRegistry();
    }

    @Override
    public void onEnable() {
        // Tras el server start: cargar mundos persistidos que no se hayan cargado vanilla.
        this.worldsManager.loadStartupWorlds();

        this.lazyTeleportService = new LazyTeleportService(this);
        this.idleWorldUnloader = new IdleWorldUnloader(this);
        this.backupManager = new BackupManager(this);
        this.etcCoreHook = new ETCCoreHook(this);
        this.regionGenHook = new RegionGenHook(this);
        this.worldGroupsManager = new WorldGroupsManager(this);
        this.instanceManager = new InstanceManager(this);
        this.templateCloneService = new TemplateCloneService(this);
        this.worldsGUI = new WorldsGUI(this);
        this.pocketRulesGUI = new PocketRulesGUI(this);
        this.customPortalManager = new CustomPortalManager(this);
        this.customPortalManager.load();
        this.pocketWorldManager = new PocketWorldManager(this);
        this.pocketWorldManager.load();
        // Limpia mundos marcados como pending-delete (Folia: no se pudo unload en runtime).
        this.pocketWorldManager.processPendingDeletes();

        // Comandos
        bind("etcworlds", new WorldsCommand(this));
        WorldTpCommand tpCmd = new WorldTpCommand(this);
        bind("world", tpCmd);
        bind("worldlist", tpCmd);
        bind("worldinfo", tpCmd);
        bind("worldspawn", tpCmd);
        bind("spawn", tpCmd);
        bind("lobby", tpCmd);
        bind("pocketworld", new PocketWorldCommand(this));

        // Listeners
        Bukkit.getPluginManager().registerEvents(new PortalLinkListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WorldRulesListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WorldAccessListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WorldGroupsListener(this), this);
        Bukkit.getPluginManager().registerEvents(new OneblockListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CustomPortalListener(this), this);
        Bukkit.getPluginManager().registerEvents(this.worldsGUI, this);
        Bukkit.getPluginManager().registerEvents(this.pocketRulesGUI, this);

        // Tareas periódicas
        this.idleWorldUnloader.start();
        this.backupManager.start();

        // PlaceholderAPI: registrar en ServerLoadEvent para garantizar que PAPI
        // ya este completamente activo (requerido en Folia y en arranques frios).
        // Fallback: tambien intentamos en onEnable por si el server ya cargo (recarga).
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                boolean ok = new PlaceholderHook(this).register();
                if (ok) getLogger().info("Hook PlaceholderAPI registrado.");
                else    getLogger().warning("PlaceholderHook.register() devolvio false (se reintentara en ServerLoadEvent).");
            }
            // Registrar listener para reintentar cuando el server este completamente cargado.
            Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void onServerLoad(io.papermc.paper.event.server.ServerResourcesReloadedEvent e) {
                    if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                        new PlaceholderHook(ETCWorlds.this).register();
                        getLogger().info("Hook PlaceholderAPI re-registrado tras reload.");
                    }
                }
            }, this);
            // Para arranque inicial (no hay ServerResourcesReloadedEvent al boot):
            // Diferir 2 ticks via global region para que PAPI termine su init.
            Bukkit.getGlobalRegionScheduler().runDelayed(this, t -> {
                if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    boolean ok = new PlaceholderHook(ETCWorlds.this).register();
                    if (ok) getLogger().info("Hook PlaceholderAPI registrado (tick-delayed).");
                }
            }, 2L);
        }

        // ETCCore hook (variables/acciones)
        this.etcCoreHook.register();
        this.regionGenHook.detect();

        getLogger().info("ETCWorlds " + getDescription().getVersion() + " habilitado.");
    }

    @Override
    public void onDisable() {
        if (idleWorldUnloader != null) idleWorldUnloader.stop();
        if (backupManager != null) backupManager.stop();
        if (pocketWorldManager != null) pocketWorldManager.save();
        if (worldsManager != null) worldsManager.shutdown();
    }

    private void bind(String name, Object handler) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Comando '" + name + "' no declarado en plugin.yml");
            return;
        }
        if (handler instanceof org.bukkit.command.CommandExecutor exe) cmd.setExecutor(exe);
        if (handler instanceof org.bukkit.command.TabCompleter tab) cmd.setTabCompleter(tab);
    }

    /**
     * Intercepción de Bukkit: cuando un mundo se carga en STARTUP por bukkit.yml o por
     * createWorld() y necesita un ChunkGenerator, Bukkit invoca este método.
     */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return worldsManager != null ? worldsManager.resolveGenerator(worldName, id) : null;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(String worldName, String id) {
        return worldsManager != null ? worldsManager.resolveBiomeProvider(worldName, id) : null;
    }

    public WorldsManager worlds() { return worldsManager; }
    public LazyTeleportService lazyTeleport() { return lazyTeleportService; }
    public IdleWorldUnloader idleUnloader() { return idleWorldUnloader; }
    public BackupManager backups() { return backupManager; }
    public ETCCoreHook etcCore() { return etcCoreHook; }
    public RegionGenHook regionGen() { return regionGenHook; }
    public WorldGroupsManager groups() { return worldGroupsManager; }
    public InstanceManager instances() { return instanceManager; }
    public TemplateCloneService templates() { return templateCloneService; }
    public WorldsGUI gui() { return worldsGUI; }
    public PocketRulesGUI pocketRulesGUI() { return pocketRulesGUI; }
    public CustomPortalManager portals() { return customPortalManager; }
    public PocketWorldManager pocketWorlds() { return pocketWorldManager; }
}
