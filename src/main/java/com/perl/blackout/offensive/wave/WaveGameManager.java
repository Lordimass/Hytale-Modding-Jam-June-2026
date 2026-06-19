package com.perl.blackout.offensive.wave;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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
    /** Block id used to clear (break) a block in the world. */
    private static final String EMPTY_BLOCK = "Empty";

    private final WaveConfig config;
    private final EnemySpawnService enemySpawnService = new EnemySpawnService();
    private final ObjectiveService objectiveService = new ObjectiveService();
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
        ensureGame(world);
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

        long elapsed = game.getPhaseElapsedMs(now);
        switch (game.getPhase()) {
            case REST -> {
                if (elapsed >= config.restDurationSeconds * 1000L) {
                    beginAttack(world, game, now);
                }
            }
            case ATTACK -> {
                if (elapsed >= config.attackDurationSeconds * 1000L) {
                    beginRest(world, game, now);
                } else {
                    world.execute(() ->
                            objectiveService.applyTargeting(game, world.getEntityStore().getStore(), world));
                }
            }
            default -> {
            }
        }
    }

    private void beginAttack(World world, WaveGame game, long now) {
        game.startPhase(WavePhase.ATTACK, now);
        int round = game.incrementRound();
        WaveMessages.broadcast(world, "Night " + round, "The Seekers are coming!", true);

        WaveConfig.Floor floor = config.findFloor(game.getFloor());
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            setLightPhase(world, store, PHASE_DARK);
            enemySpawnService.spawnFloorEnemies(game, store, world, config, floor);
            objectiveService.applyTargeting(game, store, world);
        });
    }

    private void beginRest(World world, WaveGame game, long now) {
        game.startPhase(WavePhase.REST, now);
        WaveMessages.broadcast(world, "Daybreak",
                "Rest " + config.restDurationSeconds + "s until the next night.", true);
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            setLightPhase(world, store, PHASE_LIT);
            enemySpawnService.despawnAll(game, store);
        });
    }

    private void endGame(World world, WaveGame game) {
        game.startPhase(WavePhase.ENDED, System.currentTimeMillis());
        games.remove(world);
        world.execute(() -> enemySpawnService.despawnAll(game, world.getEntityStore().getStore()));
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

    /** Clears the bench block and drops the crafting machine item (used when the bench NPC dies). */
    private void breakAndDropBench(World world, Vector3i blockPos) {
        world.setBlock(blockPos.x, blockPos.y, blockPos.z, EMPTY_BLOCK, 0);
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

    private boolean matchesInstance(World world) {
        String name = world.getName();
        return name != null && config.instanceWorldMatch != null
                && name.toLowerCase().contains(config.instanceWorldMatch.toLowerCase());
    }
}
