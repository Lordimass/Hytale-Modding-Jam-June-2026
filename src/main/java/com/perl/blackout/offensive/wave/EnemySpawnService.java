package com.perl.blackout.offensive.wave;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

/**
 * Spawns and despawns the wave enemies for a floor.
 *
 * <p>Enemies appear in a ring around the players (never on top of them) at the players' own Y, so
 * they spawn on the same level as the people they hunt. With no players present they fall back to
 * the configured anchor at the floor Y.
 *
 * <p>All mutating methods must be invoked on the world thread (i.e. inside {@code world.execute(...)}),
 * since entities cannot be added or removed during system tick processing.
 */
final class EnemySpawnService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** Spawn attempts per enemy before giving up (each tries a fresh ring position). */
    private static final int SPAWN_ATTEMPTS = 5;

    private final Random random = new Random();

    /** Spawns every configured enemy for the given floor, ringed around the current players. */
    void spawnFloorEnemies(WaveGame game, Store<EntityStore> store, World world,
                           WaveConfig config, @Nullable WaveConfig.Floor floor) {
        if (floor == null) {
            LOGGER.atWarning().log("No floor config for floor %s; no enemies spawned", game.getFloor());
            return;
        }
        List<Vector3d> players = WavePlayers.positions(world, store);
        double minRadius = Math.max(1.0, config.enemySpawnDistance - config.enemySpawnDistanceJitter);
        double maxRadius = config.enemySpawnDistance + config.enemySpawnDistanceJitter;

        int spawned = 0;
        for (WaveConfig.EnemyGroup group : floor.enemies) {
            if (group.type == null || group.type.isBlank()) {
                continue;
            }
            for (int i = 0; i < group.count; i++) {
                Ref<EntityStore> ref = null;
                // Each enemy picks its own anchor + ring position, retrying so it reliably spawns.
                for (int attempt = 0; attempt < SPAWN_ATTEMPTS && ref == null; attempt++) {
                    double anchorX;
                    double anchorZ;
                    double y;
                    if (players.isEmpty()) {
                        anchorX = config.playerSpawn.x;
                        anchorZ = config.playerSpawn.z;
                        y = floor.floorY;
                    } else {
                        Vector3d anchor = players.get(random.nextInt(players.size()));
                        anchorX = anchor.x;
                        anchorZ = anchor.z;
                        y = anchor.y;
                    }
                    Vector3d position = WavePositions.findOpenRing(
                            world, anchorX, anchorZ, y, minRadius, maxRadius, random);
                    ref = spawnEnemy(store, group.type, position);
                }
                if (ref != null) {
                    game.addEnemy(ref);
                    spawned++;
                } else {
                    LOGGER.atWarning().log("Gave up spawning a '%s' after %s attempts", group.type, SPAWN_ATTEMPTS);
                }
            }
        }
        LOGGER.atInfo().log("Spawned %s enemies for floor %s", spawned, game.getFloor());
    }

    @Nullable
    private Ref<EntityStore> spawnEnemy(Store<EntityStore> store, String role, Vector3d position) {
        try {
            float yaw = (float) (random.nextDouble() * Math.PI * 2.0);
            var result = NPCPlugin.get().spawnNPC(store, role, null, position, new Rotation3f(0.0f, yaw, 0.0f));
            return result != null ? result.first() : null;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to spawn enemy '%s'", role);
            return null;
        }
    }

    /** Removes all tracked enemies and clears the game's enemy list. */
    void despawnAll(WaveGame game, Store<EntityStore> store) {
        synchronized (game.getEnemies()) {
            for (Ref<EntityStore> ref : game.getEnemies()) {
                if (ref != null && ref.isValid()) {
                    store.removeEntity(ref, RemoveReason.REMOVE);
                }
            }
        }
        game.clearEnemies();
    }
}
