package com.perl.blackout.offensive.wave;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.joml.Vector3d;
import org.joml.Vector3i;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.perl.blackout.offensive.OffensivePlugin;
import com.perl.blackout.world.resources.WorldCycleStateResource;
import com.perl.blackout.world.systems.CyclePhase;

/**
 * Owns the wave games (one per Backrooms instance world) and drives their phase transitions.
 *
 * <p>The loop is endless: REST (day) and ATTACK (night) alternate purely on their timers — there is
 * no win or loss. A player-placed crafting machine spawns an optional bench NPC that enemies prefer
 * to target; if it is destroyed the bench reverts to a re-placeable item and enemies fall back to
 * hunting players, but the waves keep coming.
 *
 * <p>Reads happen on the tick thread; all entity/world mutations are deferred to the world thread
 * via {@link World#execute(Runnable)}.
 */
public final class WaveGameManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** {@link CyclePhase}/{@link WorldCycleStateResource} arg that lights the world (REST). */
    private static final boolean PHASE_LIT = true;
    /** {@link CyclePhase}/{@link WorldCycleStateResource} arg that darkens the world (ATTACK). */
    private static final boolean PHASE_DARK = false;

    /** Block id of the crafting machine that, when placed in an instance, becomes the bench. */
    public static final String BENCH_BLOCK_ID = "BO_CraftingMachine";
    /** NPC role spawned inside the crafting machine so enemies can target the bench. */
    public static final String BENCH_NPC_ROLE = "BO_CraftingMachineTarget";
    /** Native fence item/block id that becomes a damageable wave target inside instances. */
    public static final String FENCE_BLOCK_ID = "Wood_Softwood_Fence";
    /** NPC role spawned inside a fence so enemies can destroy it when it blocks the bench route. */
    public static final String FENCE_NPC_ROLE = "BO_FenceTarget";
    /** Block id used to clear (break) a block in the world. */
    public static final String EMPTY_BLOCK_ID = "Empty";
    /** Blocks within which a player's lit flashlight scares a Seeker (~the Seeker's view range). */
    private static final double FLASHLIGHT_SCARE_RANGE = 16.0;

    private final WaveConfig config;
    private final EnemySpawnService enemySpawnService = new EnemySpawnService();
    private final FenceTargetService fenceTargetService = new FenceTargetService();
    private final ObjectiveService objectiveService = new ObjectiveService(fenceTargetService);
    private final FlashlightScareService flashlightScareService = new FlashlightScareService(FLASHLIGHT_SCARE_RANGE);
    private final Map<World, WaveGame> games = new ConcurrentHashMap<>();

    public WaveGameManager(OffensivePlugin plugin) {
        this.config = WaveConfig.load(WaveConfig.ensureRuntimeConfig(plugin.getDataDirectory()));
    }

    public WaveGame getGame(World world) {
        return games.get(world);
    }

    // ── Lifecycle wiring (called from OffensivePlugin event handlers) ──

    /** Starts a wave game when a player first enters a matching Backrooms instance world. */
    public void onPlayerAddedToWorld(PlayerRef playerRef, World world) {
        WaveGame game = ensureGame(world);
        if (game != null && game.isInitialized()) {
            world.execute(() -> {
                Store<EntityStore> store = world.getEntityStore().getStore();
                WaveConfig.Floor floor = resolveCurrentFloor(world, store, game);
                if (floor != null && floor.spawnEnemiesOnInitialize) {
                    enemySpawnService.ensurePersistentFloorEnemies(game, store, world, config, floor);
                    objectiveService.applyTargeting(game, store, world);
                    flashlightScareService.apply(game, store, world);
                }
            });
        }
    }

    public void onPlayerRemovedFromWorld(PlayerRef playerRef, World world) {
        WaveGame game = games.get(world);
        if (game != null && world.getPlayerRefs().isEmpty()) {
            endGame(world, game);
        }
    }

    public void onWorldRemoved(World world) {
        WaveGame game = games.remove(world);
        if (game != null) {
            game.startPhase(WavePhase.ENDED, System.currentTimeMillis());
        }
    }

    public void shutdown() {
        games.clear();
    }

    // ── Game creation ──

    /** Creates (once) the wave game for a Backrooms instance world. No-op for non-instance worlds. */
    public WaveGame ensureGame(World world) {
        if (!matchesInstance(world)) {
            return null;
        }
        return games.computeIfAbsent(world, w -> {
            int floor = config.floors.isEmpty() ? 0 : config.floors.get(0).floor;
            WaveGame game = new WaveGame(w, floor);
            awaitChunksThenInitialize(world, game);
            LOGGER.atInfo().log("Wave game pending world load in %s", world.getName());
            return game;
        });
    }

    /** Loads the anchor chunk asynchronously, then initialises the game on the world thread. */
    private void awaitChunksThenInitialize(World world, WaveGame game) {
        long anchorChunk = ChunkUtil.indexChunkFromBlock(
                (int) Math.floor(config.playerSpawn.x), (int) Math.floor(config.playerSpawn.z));

        CompletableFuture<?> ready = world.getChunkAsync(anchorChunk);
        ready.whenComplete((ignored, error) -> {
            if (error != null) {
                // Not fatal: the players are already in the world, so still start the game.
                LOGGER.atWarning().withCause(error).log("Anchor chunk preload failed; starting wave game anyway");
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

        setLightPhase(world, store, PHASE_LIT);
        WaveConfig.Floor floor = resolveCurrentFloor(world, store, game);
        if (floor != null && floor.spawnEnemiesOnInitialize) {
            enemySpawnService.ensurePersistentFloorEnemies(game, store, world, config, floor);
            objectiveService.applyTargeting(game, store, world);
            flashlightScareService.apply(game, store, world);
        }

        game.startPhase(WavePhase.REST, System.currentTimeMillis());
        game.setInitialized(true);
        WaveMessages.broadcast(world, "The Backrooms",
                "Stay in the light. Night falls in " + config.restDurationSeconds + "s.", true);
        LOGGER.atInfo().log("Started wave game in world %s", world.getName());
    }

    // ── Per-tick driver (called from WaveSystem) ──

    public void advance(World world, Store<EntityStore> store, long now) {
        WaveGame game = games.get(world);
        if (game == null || !game.isInitialized() || game.getPhase() == WavePhase.ENDED) {
            return;
        }

        // If the bench NPC has died, break its block and drop the item so it can be re-placed.
        if (game.hasBench() && objectiveService.isDead(store, game.getBenchNpcRef())) {
            Vector3i blockPos = game.getBenchBlockPos();
            game.clearBench();
            if (blockPos != null) {
                world.execute(() -> breakAndDropBench(world, blockPos));
            }
        }
        world.execute(() -> fenceTargetService.clearDestroyedFences(game, world.getEntityStore().getStore(), world));

        long elapsed = game.getPhaseElapsedMs(now);
        switch (game.getPhase()) {
            case REST -> {
                if (elapsed >= config.restDurationSeconds * 1000L) {
                    beginAttack(world, game, now);
                } else {
                    world.execute(() -> {
                        Store<EntityStore> restStore = world.getEntityStore().getStore();
                        enemySpawnService.pruneDeadPersistentEnemies(game, restStore);
                        objectiveService.applyTargeting(game, restStore, world);
                        flashlightScareService.apply(game, restStore, world);
                    });
                }
            }
            case ATTACK -> {
                if (elapsed >= config.attackDurationSeconds * 1000L) {
                    beginRest(world, game, now);
                } else {
                    world.execute(() -> {
                        Store<EntityStore> attackStore = world.getEntityStore().getStore();
                        enemySpawnService.pruneDeadPersistentEnemies(game, attackStore);
                        objectiveService.applyTargeting(game, attackStore, world);
                        flashlightScareService.apply(game, attackStore, world);
                    });
                }
            }
            default -> {
            }
        }
    }

    private void beginAttack(World world, WaveGame game, long now) {
        game.startPhase(WavePhase.ATTACK, now);
        int round = game.incrementRound();
        WaveMessages.broadcast(world, "Night " + round, "The lights are going out!", true);

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            WaveConfig.Floor floor = resolveCurrentFloor(world, store, game);
            setLightPhase(world, store, PHASE_DARK);
            enemySpawnService.spawnFloorEnemies(game, store, world, config, floor);
            objectiveService.applyTargeting(game, store, world);
            flashlightScareService.apply(game, store, world);
        });
    }

    private void beginRest(World world, WaveGame game, long now) {
        game.startPhase(WavePhase.REST, now);
        WaveMessages.broadcast(world, "Daybreak",
                "Rest " + config.restDurationSeconds + "s until the next night.", true);
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            WaveConfig.Floor floor = resolveCurrentFloor(world, store, game);
            setLightPhase(world, store, PHASE_LIT);
            enemySpawnService.despawnAll(game, store);
            if (floor != null && floor.persistentEnemies) {
                enemySpawnService.ensurePersistentFloorEnemies(game, store, world, config, floor);
            }
            objectiveService.applyTargeting(game, store, world);
            flashlightScareService.apply(game, store, world);
        });
    }

    private void endGame(World world, WaveGame game) {
        game.startPhase(WavePhase.ENDED, System.currentTimeMillis());
        games.remove(world);
        world.execute(() -> enemySpawnService.despawnAllIncludingPersistent(game, world.getEntityStore().getStore()));
        world.execute(() -> fenceTargetService.removeAllFenceTargets(game, world.getEntityStore().getStore()));
        LOGGER.atInfo().log("Ended wave game in world %s (no players left)", world.getName());
    }

    // ── Bench (crafting machine) ──

    /** Spawns the bench NPC for a placed crafting machine and registers it as the preferred target. */
    public void onBenchPlaced(World world, Vector3i blockPos) {
        WaveGame game = ensureGame(world);
        if (game == null) {
            return;
        }
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            if (game.hasBench()) {
                return; // one bench at a time
            }
            Vector3d npcPos = new Vector3d(blockPos.x + 0.5, blockPos.y, blockPos.z + 0.5);
            Ref<EntityStore> ref = objectiveService.spawnBench(store, npcPos);
            if (ref != null) {
                game.setBench(ref, new Vector3i(blockPos));
                objectiveService.applyTargeting(game, store, world);
                WaveMessages.broadcast(world, "Workbench Online", "The Seekers will hunt it now.", false);
            }
        });
    }

    /** A player broke the crafting machine: despawn its NPC and drop the bench item. */
    public void onBenchBroken(World world, Vector3i blockPos) {
        WaveGame game = games.get(world);
        Ref<EntityStore> npc = null;
        if (game != null && game.hasBench()) {
            Vector3i benchPos = game.getBenchBlockPos();
            if (benchPos != null && benchPos.x == blockPos.x && benchPos.y == blockPos.y && benchPos.z == blockPos.z) {
                npc = game.getBenchNpcRef();
                game.clearBench();
            }
        }

        Ref<EntityStore> benchNpc = npc;
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            if (benchNpc != null && benchNpc.isValid()) {
                store.removeEntity(benchNpc, RemoveReason.REMOVE);
            }
            dropBenchItem(store, blockPos);
        });
    }

    /** Spawns the damageable target inside a player-placed fence in an instance. */
    public void onFencePlaced(World world, Vector3i blockPos) {
        WaveGame game = ensureGame(world);
        if (game == null) {
            return;
        }
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            Ref<EntityStore> existing = game.removeFenceTarget(blockPos);
            fenceTargetService.removeFenceTarget(store, existing);
            Ref<EntityStore> ref = fenceTargetService.spawnFenceTarget(store, blockPos);
            if (ref != null) {
                game.setFenceTarget(blockPos, ref);
                objectiveService.applyTargeting(game, store, world);
            }
        });
    }

    /** Removes the hidden fence target when a player breaks the visible fence block. */
    public void onFenceBroken(World world, Vector3i blockPos) {
        WaveGame game = games.get(world);
        if (game == null) {
            return;
        }
        Ref<EntityStore> ref = game.removeFenceTarget(blockPos);
        world.execute(() -> fenceTargetService.removeFenceTarget(world.getEntityStore().getStore(), ref));
    }

    /** Clears the bench block and drops the crafting machine item (used when the bench NPC dies). */
    private void breakAndDropBench(World world, Vector3i blockPos) {
        world.setBlock(blockPos.x, blockPos.y, blockPos.z, EMPTY_BLOCK_ID, 0);
        Store<EntityStore> store = world.getEntityStore().getStore();
        dropBenchItem(store, blockPos);
        WaveMessages.broadcast(world, "Workbench Destroyed", "Pick it back up and re-place it!", false);
    }

    private void dropBenchItem(Store<EntityStore> store, Vector3i blockPos) {
        Vector3d dropPos = new Vector3d(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5);
        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(
                store, List.of(new ItemStack(BENCH_BLOCK_ID, 1)), dropPos, Rotation3f.ZERO);
        if (holders.length > 0) {
            store.addEntities(holders, AddReason.SPAWN);
        }
    }

    // ── Helpers ──

    /**
     * Drives the world blackout phase (the new world light system): {@code dark=true} switches all
     * configured light blocks off, {@code dark=false} switches them on. Replaces the old day/night
     * clock control. Must run on the world thread.
     */
    private static void setLightPhase(World world, Store<EntityStore> store, boolean lit) {
        WorldCycleStateResource state = store.getResource(WorldCycleStateResource.getResourceType());
        if (state != null) {
            state.setOn(lit);
        }
        CyclePhase.applyPhaseToWorld(world, lit);
    }

    @Nullable
    private WaveConfig.Floor resolveCurrentFloor(World world, Store<EntityStore> store, WaveGame game) {
        WaveConfig.Floor floor = nearestFloorForPlayers(world, store);
        if (floor == null) {
            floor = config.findFloor(game.getFloor());
        }
        if (floor == null && !config.floors.isEmpty()) {
            floor = config.floors.get(0);
        }
        if (floor != null) {
            game.setFloor(floor.floor);
        }
        return floor;
    }

    @Nullable
    private WaveConfig.Floor nearestFloorForPlayers(World world, Store<EntityStore> store) {
        List<Vector3d> players = WavePlayers.positions(world, store);
        if (players.isEmpty() || config.floors.isEmpty()) {
            return null;
        }
        WaveConfig.Floor best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Vector3d player : players) {
            for (WaveConfig.Floor floor : config.floors) {
                double distance = Math.abs(player.y - floor.floorY);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = floor;
                }
            }
        }
        return best;
    }

    private boolean matchesInstance(World world) {
        String name = world.getName();
        return name != null && config.instanceWorldMatch != null
                && name.toLowerCase().contains(config.instanceWorldMatch.toLowerCase());
    }
}
