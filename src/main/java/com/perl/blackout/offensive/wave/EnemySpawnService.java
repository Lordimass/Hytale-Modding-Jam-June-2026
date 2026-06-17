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
import java.util.Random;

/**
 * Spawns and despawns the wave enemies for a floor.
 *
 * <p>All mutating methods must be invoked on the world thread (i.e. inside {@code world.execute(...)}),
 * since entities cannot be added or removed during system tick processing.
 */
final class EnemySpawnService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Random random = new Random();

    /** Spawns every configured enemy for the given floor around the objective centre. */
    void spawnFloorEnemies(WaveGame game, Store<EntityStore> store, World world,
                           @Nullable WaveConfig.Floor floor, WaveConfig.Position center) {
        if (floor == null) {
            LOGGER.atWarning().log("No floor config for floor %s; no enemies spawned", game.getFloor());
            return;
        }
        int spawned = 0;
        for (WaveConfig.EnemyGroup group : floor.enemies) {
            if (group.type == null || group.type.isBlank()) {
                continue;
            }
            for (int i = 0; i < group.count; i++) {
                Vector3d position = WavePositions.findOpen(
                        world, center.x, center.z, floor.floorY, floor.enemySpawnRadius, random);
                Ref<EntityStore> ref = spawnEnemy(store, group.type, position);
                if (ref != null) {
                    game.addEnemy(ref);
                    spawned++;
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
