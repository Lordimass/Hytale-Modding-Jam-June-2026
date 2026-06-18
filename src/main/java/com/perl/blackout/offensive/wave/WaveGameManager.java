package com.perl.blackout.offensive.wave;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3d;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.perl.blackout.offensive.OffensivePlugin;

/**
 * Owns the wave games (one per Backrooms instance world) and drives their phase transitions.
 *
 * <p>Reads happen on the tick thread; all entity/world mutations are deferred to the world thread
 * via {@link World#execute(Runnable)}.
 */
public final class WaveGameManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double TIME_MIDNIGHT = 0.0; // night
    private static final double TIME_NOON = 0.5;     // day
    /** Radius (blocks) the fixed player spawn is searched for an open spot. */
    private static final double PLAYER_SPAWN_SEARCH_RADIUS = 4.0;

    private final WaveConfig config;
    private final EnemySpawnService enemySpawnService = new EnemySpawnService();
    private final ObjectiveService objectiveService = new ObjectiveService();
    private final PlayerSpawnService playerSpawnService = new PlayerSpawnService();
    private final Map<World, WaveGame> games = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public WaveGameManager(OffensivePlugin plugin) {
        this.config = WaveConfig.load(WaveConfig.ensureRuntimeConfig(plugin.getDataDirectory()));
    }

    public WaveGame getGame(World world) {
        return games.get(world);
    }

    public void onPlayerAddedToWorld(PlayerRef playerRef, Player player, World world) {
        if (!matchesInstance(world)) {
            return;
        }
        WaveGame game = ensureGame(world);

        if (!game.isInitialized()) {
            return;
        }
        Vector3d spawn = game.getPlayerSpawn();
        if (spawn == null) {
            return;
        }
        var playerEntityRef = playerRef.getReference();
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            // playerSpawnService.teleport(playerEntityRef, store, world, spawn);
        });
    }

    public void onPlayerRemovedFromWorld(PlayerRef playerRef, Player player, World world) {
        WaveGame game = games.get(world);
        if (game == null) {
            return;
        }
        if (world.getPlayerRefs().isEmpty()) {
            endGame(world, game, false);
        }
    }

    public void onWorldRemoved(World world) {
        games.remove(world);
    }

    public void shutdown() {
        games.clear();
    }

    // ── Game creation ──

    private WaveGame ensureGame(World world) {
        return games.computeIfAbsent(world, w -> {
            int floor = config.floors.isEmpty() ? 0 : config.floors.get(0).floor;
            WaveGame game = new WaveGame(w, config.objective.maxHealth, floor);

            awaitChunksThenInitialize(world, game);
            LOGGER.atInfo().log("Wave game pending world load in %s", world.getName());
            return game;
        });
    }

    /** Loads the spawn + objective chunks asynchronously, then initialises the game on the world thread. */
    private void awaitChunksThenInitialize(World world, WaveGame game) {
        long spawnChunk = ChunkUtil.indexChunkFromBlock(
                (int) Math.floor(config.playerSpawn.x), (int) Math.floor(config.playerSpawn.z));
        long objectiveChunk = ChunkUtil.indexChunkFromBlock(
                (int) Math.floor(config.objective.position.x), (int) Math.floor(config.objective.position.z));

        CompletableFuture<?> ready = CompletableFuture.allOf(
                world.getChunkAsync(spawnChunk), world.getChunkAsync(objectiveChunk));
        ready.whenComplete((ignored, error) -> {
            if (error != null) {
                LOGGER.atWarning().withCause(error).log("Failed to load instance chunks for wave game");
                return;
            }
            world.execute(() -> initializeGame(world, game));
        });
    }

    /** Runs once on the world thread after the instance has finished loading. */
    private void initializeGame(World world, WaveGame game) {
        if (game.isInitialized()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();

        world.getWorldConfig().setGameTimePaused(true);
        setTime(world, store, TIME_NOON);

        Vector3d spawn = resolvePlayerSpawn(world);
        game.setPlayerSpawn(spawn);
        objectiveService.spawnObjective(game, store, config.objective);

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            if (playerRef != null && playerRef.isValid()) {
                // playerSpawnService.teleport(playerRef.getReference(), store, world, spawn);
            }
        }

        game.startPhase(WavePhase.PREP, System.currentTimeMillis());
        game.setInitialized(true);
        WaveMessages.broadcast(world, "Prepare!",
                "Defend the machine! Night falls in " + config.prepDurationSeconds + "s.", true);
        LOGGER.atInfo().log("Started wave game in world %s", world.getName());
    }

    // ── Per-tick driver (called from WaveSystem) ──

    public void advance(World world, Store<EntityStore> store, long now) {
        WaveGame game = games.get(world);
        if (game == null || !game.isInitialized() || game.getPhase() == WavePhase.ENDED) {
            return;
        }

        if (game.isObjectiveSpawned() && objectiveService.isDestroyed(game, store)) {
            endGame(world, game, true);
            return;
        }

        long elapsed = game.getPhaseElapsedMs(now);
        switch (game.getPhase()) {
            case PREP -> {
                if (elapsed >= config.prepDurationSeconds * 1000L) {
                    beginAttack(world, game, now);
                }
            }
            case ATTACK -> {
                if (elapsed >= config.roundDurationSeconds * 1000L) {
                    beginRest(world, game, now);
                }
            }
            case REST -> {
                if (elapsed >= config.roundDurationSeconds * 1000L) {
                    beginAttack(world, game, now);
                }
            }
            default -> {
            }
        }
    }

    private void beginAttack(World world, WaveGame game, long now) {
        game.startPhase(WavePhase.ATTACK, now);
        int round = game.incrementRound();
        WaveMessages.broadcast(world, "Night " + round, "The Seekers are coming! Defend the machine!", true);

        WaveConfig.Floor floor = config.findFloor(game.getFloor());
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            setTime(world, store, TIME_MIDNIGHT);
            enemySpawnService.spawnFloorEnemies(game, store, world, floor, config.objective.position);
            objectiveService.applyTargeting(game, store);
        });
    }

    private void beginRest(World world, WaveGame game, long now) {
        game.startPhase(WavePhase.REST, now);
        WaveMessages.broadcast(world, "Daybreak",
                "Rest " + config.roundDurationSeconds + "s until the next night.", true);
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            setTime(world, store, TIME_NOON);
            enemySpawnService.despawnAll(game, store);
        });
    }

    private void endGame(World world, WaveGame game, boolean objectiveDestroyed) {
        game.startPhase(WavePhase.ENDED, System.currentTimeMillis());
        games.remove(world);
        if (objectiveDestroyed) {
            WaveMessages.broadcast(world, "The machine is destroyed!", "You failed to defend it.", true);
        }
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            enemySpawnService.despawnAll(game, store);
        });
        LOGGER.atInfo().log("Ended wave game in world %s (objectiveDestroyed=%s)", world.getName(), objectiveDestroyed);
    }

    // ── Helpers ──

    private Vector3d resolvePlayerSpawn(World world) {
        return WavePositions.findOpen(world, config.playerSpawn.x, config.playerSpawn.z,
                config.playerSpawn.y, PLAYER_SPAWN_SEARCH_RADIUS, random);
    }

    private void setTime(World world, Store<EntityStore> store, double dayTime) {
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        if (time != null) {
            time.setDayTime(dayTime, world, store);
        }
    }

    private boolean matchesInstance(World world) {
        String name = world.getName();
        return name != null && config.instanceWorldMatch != null
                && name.toLowerCase().contains(config.instanceWorldMatch.toLowerCase());
    }
}
