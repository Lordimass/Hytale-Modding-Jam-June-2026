package com.perl.blackout.offensive.wave;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;
import org.joml.Vector3i;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;

/**
 * Manages the optional defended bench NPC and points enemies at their target each tick:
 * the bench NPC while it is alive, otherwise the nearest player.
 */
final class ObjectiveService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** Target slot consumed by NPC roles that pursue a marked entity. */
    private static final String TARGET_SLOT = MarkedEntitySupport.DEFAULT_TARGET_SLOT;
    /** Health applied to the bench NPC on spawn. */
    static final float BENCH_MAX_HEALTH = 200.0f;

    private final FenceTargetService fenceTargetService;

    ObjectiveService(FenceTargetService fenceTargetService) {
        this.fenceTargetService = fenceTargetService;
    }

    /** Spawns the bench NPC and applies its health. Must run on the world thread. */
    @Nullable
    Ref<EntityStore> spawnBench(Store<EntityStore> store, Vector3d position) {
        try {
            var result = NPCPlugin.get().spawnNPC(store, WaveGameManager.BENCH_NPC_ROLE, null, position,
                    new Rotation3f(0.0f, 0.0f, 0.0f));
            Ref<EntityStore> ref = result != null ? result.first() : null;
            if (ref == null) {
                LOGGER.atWarning().log("Failed to spawn bench NPC '%s'", WaveGameManager.BENCH_NPC_ROLE);
                return null;
            }
            EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
            if (stats != null) {
                stats.setStatValue(DefaultEntityStatTypes.getHealth(), BENCH_MAX_HEALTH);
            }
            LOGGER.atInfo().log("Spawned bench NPC '%s' with %s HP", WaveGameManager.BENCH_NPC_ROLE,
                    BENCH_MAX_HEALTH);
            return ref;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Error spawning bench NPC");
            return null;
        }
    }

    /** True if the ref is gone or its health has hit zero. Read-only; safe on any thread. */
    boolean isDead(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return true;
        }
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null) {
            return false;
        }
        EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
        return health == null || health.get() <= 0.0f;
    }

    /**
     * Re-points every live enemy at its target: the bench NPC while it lives, otherwise the nearest
     * player. Roles that ignore the {@code LockedTarget} slot fall back to their own aggro. Must run
     * on the world thread.
     */
    void applyTargeting(WaveGame game, Store<EntityStore> store, World world) {
        Ref<EntityStore> bench = game.getBenchNpcRef();
        boolean benchAlive = bench != null && !isDead(store, bench);
        List<Ref<EntityStore>> players = WavePlayers.refs(world);

        for (Ref<EntityStore> enemy : game.getAllEnemiesSnapshot()) {
            if (enemy == null || !enemy.isValid()) {
                continue;
            }
            NPCEntity npc = store.getComponent(enemy, NPCEntity.getComponentType());
            if (npc == null) {
                continue;
            }
            Role role = npc.getRole();
            if (role == null) {
                continue;
            }
            Ref<EntityStore> target = benchAlive ? targetForBenchRoute(game, store, world, enemy, bench, players) :
                    targetForNearestPlayerRoute(game, store, world, enemy, players);
            if (target == null) {
                continue;
            }
            try {
                role.setMarkedTarget(TARGET_SLOT, target);
            } catch (Exception ignored) {
                // Role does not expose this target slot; ignore.
            }
        }
    }

    @Nullable
    private Ref<EntityStore> targetForBenchRoute(WaveGame game, Store<EntityStore> store, World world,
                                                 Ref<EntityStore> enemy, Ref<EntityStore> bench,
                                                 List<Ref<EntityStore>> players) {
        Vector3d enemyPos = WavePlayers.positionOf(store, enemy);
        Vector3i benchPos = game.getBenchBlockPos();
        if (enemyPos == null || benchPos == null) {
            return bench;
        }
        FenceTargetService.RouteTarget route = fenceTargetService.resolveBenchRoute(game, store, world, enemyPos,
                benchPos);
        if (route.fenceTarget() != null) {
            return route.fenceTarget();
        }
        return bench;
    }

    @Nullable
    private Ref<EntityStore> targetForNearestPlayerRoute(WaveGame game, Store<EntityStore> store, World world,
                                                         Ref<EntityStore> enemy,
                                                         List<Ref<EntityStore>> players) {
        Ref<EntityStore> player = nearestPlayer(store, enemy, players);
        if (player == null) {
            return null;
        }
        Vector3d enemyPos = WavePlayers.positionOf(store, enemy);
        Vector3d playerPos = WavePlayers.positionOf(store, player);
        if (enemyPos == null || playerPos == null) {
            return player;
        }
        Vector3i playerBlock = new Vector3i((int) Math.floor(playerPos.x), (int) Math.floor(playerPos.y),
                (int) Math.floor(playerPos.z));
        FenceTargetService.RouteTarget route = fenceTargetService.resolvePlayerRoute(game, store, world, enemyPos,
                playerBlock);
        return route.fenceTarget() != null ? route.fenceTarget() : player;
    }

    @Nullable
    private Ref<EntityStore> nearestPlayer(Store<EntityStore> store, Ref<EntityStore> enemy,
                                           List<Ref<EntityStore>> players) {
        if (players.isEmpty()) {
            return null;
        }
        Vector3d enemyPos = WavePlayers.positionOf(store, enemy);
        if (enemyPos == null) {
            return players.get(0);
        }
        Ref<EntityStore> best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Ref<EntityStore> player : players) {
            Vector3d playerPos = WavePlayers.positionOf(store, player);
            if (playerPos == null) {
                continue;
            }
            double distSq = enemyPos.distanceSquared(playerPos);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = player;
            }
        }
        return best;
    }
}
