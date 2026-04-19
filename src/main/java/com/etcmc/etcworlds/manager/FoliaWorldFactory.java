package com.etcmc.etcworlds.manager;

import com.etcmc.etcworlds.ETCWorlds;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import io.papermc.paper.FeatureHooks;
import io.papermc.paper.world.PaperWorldLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.generator.CraftWorldInfo;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Crea mundos en tiempo real en Folia construyendo manualmente el ServerLevel via NMS.
 * Inspirado en TheNextLvl/worlds (https://github.com/TheNextLvl-net/worlds), licencia MIT.
 *
 * Funciona porque ETCWorlds usa paperweight con MOJANG_PRODUCTION reobf, lo que da
 * acceso a las clases NMS de Folia/Paper en compile-time. Debe llamarse desde el
 * GlobalRegionScheduler (tick global) ya que toca el server registry.
 */
public final class FoliaWorldFactory {

    private static final boolean RUNNING_FOLIA;

    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) { /* no es Folia */ }
        RUNNING_FOLIA = folia;
    }

    private FoliaWorldFactory() {}

    public static boolean isFolia() { return RUNNING_FOLIA; }

    public static World createWorld(ETCWorlds plugin, WorldCreator creator) {
        try {
            return createWorldImpl(plugin, creator);
        } catch (Throwable t) {
            plugin.getLogger().warning("[ETCWorlds] FoliaWorldFactory fallo creando '"
                    + creator.name() + "': " + t.getClass().getSimpleName() + ": " + t.getMessage());
            t.printStackTrace();
            return null;
        }
    }

    private static World createWorldImpl(ETCWorlds plugin, WorldCreator creator) throws Exception {
        final CraftServer server = (CraftServer) Bukkit.getServer();
        final MinecraftServer console = server.getServer();

        // El "name" puede contener subdirectorios (mundos/foo). Bukkit toma como nombre
        // simple el ultimo segmento; el directorio se forma con worldContainer + name.
        final String fullName = creator.name();
        final String simpleName = fullName.contains("/")
                ? fullName.substring(fullName.lastIndexOf('/') + 1)
                : fullName;
        final Path directory = new File(Bukkit.getWorldContainer(), fullName).toPath();

        Preconditions.checkState(console.getAllLevels().iterator().hasNext(),
                "Cannot create worlds before main level is created");
        Preconditions.checkArgument(!Files.exists(directory) || Files.isDirectory(directory),
                "Path %s exists and is not a folder", directory);
        Preconditions.checkArgument(server.getWorld(simpleName) == null,
                "World with name %s already exists", simpleName);

        ChunkGenerator chunkGenerator = creator.generator();
        if (chunkGenerator == null) chunkGenerator = server.getGenerator(simpleName);
        BiomeProvider biomeProvider = creator.biomeProvider();
        if (biomeProvider == null) biomeProvider = server.getBiomeProvider(simpleName);

        final ResourceKey<LevelStem> dimensionType = mapEnvironment(creator.environment());

        final LevelStorageSource.LevelStorageAccess levelStorageAccess =
                LevelStorageSource.createDefault(directory.getParent())
                        .validateAndCreateAccess(directory.getFileName().toString(), dimensionType);

        final WorldLoader.DataLoadContext context = console.worldLoaderContext;
        RegistryAccess.Frozen registryAccess = context.datapackDimensions();
        Registry<LevelStem> stemRegistry = registryAccess.lookupOrThrow(Registries.LEVEL_STEM);

        final var levelDataResult = PaperWorldLoader.getLevelData(levelStorageAccess);
        if (levelDataResult.fatalError())
            throw new RuntimeException("Failed to read level data");

        final Dynamic<?> dataTag = levelDataResult.dataTag();
        final PrimaryLevelData primaryLevelData;

        if (dataTag != null) {
            final LevelDataAndDimensions lad = LevelStorageSource.getLevelDataAndDimensions(
                    dataTag, context.dataConfiguration(), stemRegistry, context.datapackWorldgen());
            primaryLevelData = (PrimaryLevelData) lad.worldData();
            registryAccess = lad.dimensions().dimensionsRegistryAccess();
        } else {
            final long seed = creator.seed();
            final WorldOptions worldOptions = new WorldOptions(seed, creator.generateStructures(), false);
            final DedicatedServerProperties.WorldDimensionData wdd =
                    new DedicatedServerProperties.WorldDimensionData(
                            new com.google.gson.JsonObject(),
                            mapWorldType(creator.type()));
            final LevelSettings levelSettings = new LevelSettings(
                    simpleName,
                    GameType.byId(server.getDefaultGameMode().getValue()),
                    false, Difficulty.EASY, false,
                    new GameRules(context.dataConfiguration().enabledFeatures()),
                    context.dataConfiguration()
            );
            final WorldDimensions worldDimensions = wdd.create(context.datapackWorldgen());
            final WorldDimensions.Complete complete = worldDimensions.bake(stemRegistry);
            final Lifecycle lifecycle = complete.lifecycle().add(context.datapackWorldgen().allRegistriesLifecycle());
            primaryLevelData = new PrimaryLevelData(levelSettings, worldOptions, complete.specialWorldProperty(), lifecycle);
            registryAccess = complete.dimensionsRegistryAccess();
        }

        stemRegistry = registryAccess.lookupOrThrow(Registries.LEVEL_STEM);
        primaryLevelData.customDimensions = stemRegistry;
        primaryLevelData.checkName(simpleName);
        primaryLevelData.setModdedInfo(console.getServerModName(),
                console.getModdedStatus().shouldReportAsModified());

        final long obfSeed = BiomeManager.obfuscateSeed(primaryLevelData.worldGenOptions().seed());
        final List<CustomSpawner> spawners = ImmutableList.of(
                new PhantomSpawner(), new PatrolSpawner(),
                new CatSpawner(), new VillageSiege(),
                new WanderingTraderSpawner(primaryLevelData)
        );
        final LevelStem customStem = stemRegistry.getValueOrThrow(dimensionType);

        final World.Environment env = creator.environment();
        final WorldInfo worldInfo = new CraftWorldInfo(
                primaryLevelData, levelStorageAccess, env,
                customStem.type().value(), customStem.generator(),
                server.getHandle().getServer().registryAccess()
        );
        if (biomeProvider == null && chunkGenerator != null)
            biomeProvider = chunkGenerator.getDefaultBiomeProvider(worldInfo);

        final String defaultLevelName =
                ((net.minecraft.server.dedicated.DedicatedServer) console).getProperties().levelName;
        final ResourceKey<Level> dimensionKey;
        if (simpleName.equals(defaultLevelName + "_nether")) dimensionKey = Level.NETHER;
        else if (simpleName.equals(defaultLevelName + "_the_end")) dimensionKey = Level.END;
        else dimensionKey = ResourceKey.create(Registries.DIMENSION,
                Identifier.fromNamespaceAndPath("etcworlds", simpleName.toLowerCase(Locale.ROOT)));

        final ServerLevel serverLevel = new ServerLevel(
                console, console.executor, levelStorageAccess,
                primaryLevelData, dimensionKey, customStem,
                primaryLevelData.isDebugWorld(), obfSeed,
                env == World.Environment.NORMAL ? spawners : ImmutableList.of(),
                true, console.overworld().getRandomSequences(),
                env, chunkGenerator, biomeProvider
        );

        primaryLevelData.setInitialized(false);
        console.addLevel(serverLevel);
        console.initWorld(serverLevel, primaryLevelData, primaryLevelData.worldGenOptions());
        serverLevel.setSpawnSettings(true);
        FeatureHooks.tickEntityManager(serverLevel);
        console.prepareLevel(serverLevel);

        return serverLevel.getWorld();
    }

    private static ResourceKey<LevelStem> mapEnvironment(World.Environment env) {
        return switch (env) {
            case NETHER -> LevelStem.NETHER;
            case THE_END -> LevelStem.END;
            default -> LevelStem.OVERWORLD;
        };
    }

    private static String mapWorldType(org.bukkit.WorldType t) {
        if (t == null) return "minecraft:normal";
        return switch (t) {
            case FLAT -> "minecraft:flat";
            case AMPLIFIED -> "minecraft:amplified";
            case LARGE_BIOMES -> "minecraft:large_biomes";
            default -> "minecraft:normal";
        };
    }
}