package com.perl.blackout.offensive.wave;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.joml.Vector3d;
import org.joml.Vector3i;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;

/**
 * Tracks player-placed fence blocks as damageable NPC targets and finds fence blockers between
 * enemies and the workbench. The visible block remains the real fence; the NPC is the attackable
 * body inside it.
 */
final class FenceTargetService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final int FENCE_MAX_HEALTH = 80;
    private static final int PATH_MARGIN = 12;
    private static final int MAX_PATH_NODES = 8192;
    private static final int[][] NEIGHBORS = {
            {1, 0},
            {-1, 0},
            {0, 1},
            {0, -1}
    };

    @Nullable
    Ref<EntityStore> spawnFenceTarget(Store<EntityStore> store, Vector3i blockPos) {
        try {
            Vector3d position = new Vector3d(blockPos.x + 0.5, blockPos.y, blockPos.z + 0.5);
            var result = NPCPlugin.get().spawnNPC(store, WaveGameManager.FENCE_NPC_ROLE, null, position,
                    Rotation3f.ZERO);
            Ref<EntityStore> ref = result != null ? result.first() : null;
            if (ref == null) {
                LOGGER.atWarning().log("Failed to spawn fence NPC '%s'", WaveGameManager.FENCE_NPC_ROLE);
                return null;
            }
            EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
            if (stats != null) {
                stats.setStatValue(DefaultEntityStatTypes.getHealth(), FENCE_MAX_HEALTH);
            }
            return ref;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Error spawning fence NPC");
            return null;
        }
    }

    void removeFenceTarget(Store<EntityStore> store, @Nullable Ref<EntityStore> ref) {
        if (ref != null && ref.isValid()) {
            store.removeEntity(ref, RemoveReason.REMOVE);
        }
    }

    void removeAllFenceTargets(WaveGame game, Store<EntityStore> store) {
        for (Ref<EntityStore> ref : game.getFenceTargetsSnapshot().values()) {
            removeFenceTarget(store, ref);
        }
        game.clearFenceTargets();
    }

    void clearDestroyedFences(WaveGame game, Store<EntityStore> store, World world) {
        for (Map.Entry<Vector3i, Ref<EntityStore>> entry : game.getFenceTargetsSnapshot().entrySet()) {
            Vector3i pos = entry.getKey();
            Ref<EntityStore> ref = entry.getValue();
            if (!isDead(store, ref)) {
                continue;
            }
            game.removeFenceTarget(pos);
            if (ref != null && ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
            world.setBlock(pos.x, pos.y, pos.z, WaveGameManager.EMPTY_BLOCK_ID, 0);
            LOGGER.atInfo().log("Fence at %s,%s,%s destroyed by enemies", pos.x, pos.y, pos.z);
        }
    }

    RouteTarget resolveBenchRoute(WaveGame game, Store<EntityStore> store, World world,
                                  Vector3d enemyPos, Vector3i benchBlockPos) {
        return resolveRoute(game, store, world, enemyPos, benchBlockPos, 1);
    }

    RouteTarget resolvePlayerRoute(WaveGame game, Store<EntityStore> store, World world,
                                   Vector3d enemyPos, Vector3i playerBlockPos) {
        return resolveRoute(game, store, world, enemyPos, playerBlockPos, 0);
    }

    private RouteTarget resolveRoute(WaveGame game, Store<EntityStore> store, World world,
                                     Vector3d enemyPos, Vector3i targetBlockPos, int goalRadius) {
        Map<Vector3i, Ref<EntityStore>> fences = liveFenceTargets(game, store);
        if (fences.isEmpty()) {
            return RouteTarget.toBench();
        }

        int y = (int) Math.floor(enemyPos.y);
        int startX = (int) Math.floor(enemyPos.x);
        int startZ = (int) Math.floor(enemyPos.z);
        int goalX = targetBlockPos.x;
        int goalZ = targetBlockPos.z;

        Bounds bounds = Bounds.around(startX, startZ, goalX, goalZ);
        PathResult blockedPath = floodReachable(world, fences, y, startX, startZ, goalX, goalZ, goalRadius, bounds,
                null, false);
        if (blockedPath.reachesGoal()) {
            return RouteTarget.toBench();
        }

        Vector3i bestFence = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Map.Entry<Vector3i, Ref<EntityStore>> entry : fences.entrySet()) {
            Vector3i fence = entry.getKey();
            Ref<EntityStore> ref = entry.getValue();
            if (isDead(store, ref) || !isFenceAttackableFrom(fence, y, blockedPath.visited())) {
                continue;
            }
            PathResult openedPath = floodReachable(world, fences, y, startX, startZ, goalX, goalZ, goalRadius, bounds,
                    fence, false);
            if (!openedPath.reachesGoal()) {
                continue;
            }
            double distSq = enemyPos.distanceSquared(fence.x + 0.5, fence.y + 0.5, fence.z + 0.5);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestFence = fence;
            }
        }

        if (bestFence == null) {
            bestFence = bestReachableFence(fences, store, enemyPos, targetBlockPos, y, blockedPath.visited());
        }
        return bestFence != null ? RouteTarget.fence(fences.get(bestFence)) : RouteTarget.toBench();
    }

    private Map<Vector3i, Ref<EntityStore>> liveFenceTargets(WaveGame game, Store<EntityStore> store) {
        Map<Vector3i, Ref<EntityStore>> live = new HashMap<>();
        for (Map.Entry<Vector3i, Ref<EntityStore>> entry : game.getFenceTargetsSnapshot().entrySet()) {
            if (!isDead(store, entry.getValue())) {
                live.put(entry.getKey(), entry.getValue());
            }
        }
        return live;
    }

    private PathResult floodReachable(World world, Map<Vector3i, Ref<EntityStore>> fences, int y,
                                      int startX, int startZ, int goalX, int goalZ, int goalRadius, Bounds bounds,
                                      @Nullable Vector3i ignoredFence, boolean ignoreAllFences) {
        Cell start = new Cell(bounds.clampX(startX), bounds.clampZ(startZ));
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        Set<Cell> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && visited.size() < MAX_PATH_NODES) {
            Cell cell = queue.removeFirst();
            if (Math.abs(cell.x - goalX) <= goalRadius && Math.abs(cell.z - goalZ) <= goalRadius) {
                return new PathResult(visited, true);
            }
            for (int[] neighbor : NEIGHBORS) {
                int nx = cell.x + neighbor[0];
                int nz = cell.z + neighbor[1];
                if (!bounds.contains(nx, nz)) {
                    continue;
                }
                Cell next = new Cell(nx, nz);
                if (visited.contains(next) || !isPathOpen(world, fences, nx, y, nz, ignoredFence, ignoreAllFences)) {
                    continue;
                }
                visited.add(next);
                queue.addLast(next);
            }
        }
        return new PathResult(visited, false);
    }

    private boolean isPathOpen(World world, Map<Vector3i, Ref<EntityStore>> fences, int x, int y, int z,
                               @Nullable Vector3i ignoredFence, boolean ignoreAllFences) {
        for (Vector3i fence : fences.keySet()) {
            if (fence.x != x || fence.z != z || Math.abs(fence.y - y) > 2) {
                continue;
            }
            if (!ignoreAllFences && (ignoredFence == null || !sameBlock(fence, ignoredFence))) {
                return false;
            }
            return true;
        }
        return isBlockOpen(world, x, y, z);
    }

    private boolean isBlockOpen(World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunk(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return false;
        }
        return isPassable(chunk.getBlockType(x, y, z)) && isPassable(chunk.getBlockType(x, y + 1, z));
    }

    private boolean isPassable(BlockType type) {
        return type == null || type == BlockType.EMPTY || type.getMaterial() == BlockMaterial.Empty;
    }

    private boolean isFenceAttackableFrom(Vector3i fence, int y, Set<Cell> reachable) {
        if (Math.abs(fence.y - y) > 2) {
            return false;
        }
        for (int[] neighbor : NEIGHBORS) {
            if (reachable.contains(new Cell(fence.x + neighbor[0], fence.z + neighbor[1]))) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private Vector3i bestReachableFence(Map<Vector3i, Ref<EntityStore>> fences, Store<EntityStore> store,
                                        Vector3d enemyPos, Vector3i benchBlockPos, int y,
                                        Set<Cell> reachable) {
        Vector3i best = null;
        double bestScore = Double.MAX_VALUE;
        double ax = enemyPos.x;
        double az = enemyPos.z;
        double bx = benchBlockPos.x + 0.5;
        double bz = benchBlockPos.z + 0.5;
        for (Map.Entry<Vector3i, Ref<EntityStore>> entry : fences.entrySet()) {
            Vector3i fence = entry.getKey();
            if (isDead(store, entry.getValue()) || !isFenceAttackableFrom(fence, y, reachable)) {
                continue;
            }
            double lineDistSq = distanceSqToSegment(fence.x + 0.5, fence.z + 0.5, ax, az, bx, bz);
            double enemyDistSq = enemyPos.distanceSquared(fence.x + 0.5, fence.y + 0.5, fence.z + 0.5);
            double benchDx = fence.x + 0.5 - bx;
            double benchDz = fence.z + 0.5 - bz;
            double benchDistSq = benchDx * benchDx + benchDz * benchDz;
            double score = lineDistSq * 4.0 + enemyDistSq * 0.04 + benchDistSq * 0.08;
            if (score < bestScore) {
                bestScore = score;
                best = fence;
            }
        }
        return best;
    }

    private double distanceSqToSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double lenSq = dx * dx + dz * dz;
        if (lenSq <= 0.0001) {
            double ex = px - ax;
            double ez = pz - az;
            return ex * ex + ez * ez;
        }
        double t = ((px - ax) * dx + (pz - az) * dz) / lenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double cx = ax + t * dx;
        double cz = az + t * dz;
        double ex = px - cx;
        double ez = pz - cz;
        return ex * ex + ez * ez;
    }

    private boolean isDead(Store<EntityStore> store, @Nullable Ref<EntityStore> ref) {
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

    private boolean sameBlock(Vector3i a, Vector3i b) {
        return a.x == b.x && a.y == b.y && a.z == b.z;
    }

    record RouteTarget(@Nullable Ref<EntityStore> fenceTarget, boolean benchReachable) {
        static RouteTarget toBench() {
            return new RouteTarget(null, true);
        }

        static RouteTarget fence(@Nullable Ref<EntityStore> fenceTarget) {
            return new RouteTarget(fenceTarget, false);
        }

        static RouteTarget unreachable() {
            return new RouteTarget(null, false);
        }
    }

    private record PathResult(Set<Cell> visited, boolean reachesGoal) {
    }

    private record Bounds(int minX, int maxX, int minZ, int maxZ) {
        static Bounds around(int startX, int startZ, int goalX, int goalZ) {
            return new Bounds(
                    Math.min(startX, goalX) - PATH_MARGIN,
                    Math.max(startX, goalX) + PATH_MARGIN,
                    Math.min(startZ, goalZ) - PATH_MARGIN,
                    Math.max(startZ, goalZ) + PATH_MARGIN);
        }

        boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        int clampX(int x) {
            return Math.max(minX, Math.min(maxX, x));
        }

        int clampZ(int z) {
            return Math.max(minZ, Math.min(maxZ, z));
        }
    }

    private record Cell(int x, int z) {
    }
}
