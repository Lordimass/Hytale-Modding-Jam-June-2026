package com.perl.blackout.offensive.wave;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import org.joml.Vector3d;

import java.util.Random;

/**
 * Helpers for picking spawn positions that are not embedded inside solid blocks.
 *
 * <p>Block reads are strictly non-blocking: they never force a chunk to load (which would stall
 * the world thread while the instance is still generating). A position in an unloaded chunk is
 * simply treated as not-open.
 */
public final class WavePositions {

    private static final int MAX_ATTEMPTS = 32;

    private WavePositions() {
    }

    /**
     * A position is open if the block at it and the one above are passable. The chunk is loaded if
     * necessary (we run on the world thread and spawn close to players, so it is already resident);
     * a position whose chunk cannot be resolved is treated as not-open.
     */
    static boolean isOpen(World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return false;
        }
        return isPassable(chunk.getBlockType(x, y, z)) && isPassable(chunk.getBlockType(x, y + 1, z));
    }

    private static boolean isPassable(BlockType type) {
        return type == null || type == BlockType.EMPTY || type.getMaterial() == BlockMaterial.Empty;
    }

    /**
     * Finds an open position within {@code radius} (XZ) of the centre at the given Y.
     * Falls back to the centre if no open spot is found after a fixed number of attempts.
     */
    public static Vector3d findOpen(World world, double centerX, double centerZ, double y, double radius, Random random) {
        return findOpenRing(world, centerX, centerZ, y, 0.0, radius, random);
    }

    /**
     * Finds an open position in the XZ ring between {@code minRadius} and {@code maxRadius} of the
     * centre at the given Y — used to spawn enemies at a distance from players rather than on top
     * of them. Falls back to a point at {@code maxRadius} due north if no open spot is found.
     */
    public static Vector3d findOpenRing(World world, double centerX, double centerZ, double y,
                                        double minRadius, double maxRadius, Random random) {
        int yi = (int) Math.floor(y);
        double span = Math.max(0.0, maxRadius - minRadius);
        Vector3d lastCandidate = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = minRadius + random.nextDouble() * span;
            int x = (int) Math.floor(centerX + Math.cos(angle) * dist);
            int z = (int) Math.floor(centerZ + Math.sin(angle) * dist);
            // Scan a few Y levels around the anchor so small floor height changes don't block spawns.
            for (int dy = 0; dy <= 2; dy++) {
                int up = yi + dy;
                if (isOpen(world, x, up, z)) {
                    return new Vector3d(x + 0.5, up, z + 0.5);
                }
                int down = yi - dy;
                if (dy > 0 && isOpen(world, x, down, z)) {
                    return new Vector3d(x + 0.5, down, z + 0.5);
                }
            }
            lastCandidate = new Vector3d(x + 0.5, y, z + 0.5);
        }
        // Never give up: spawn at the last random ring position (keeps spawns spread out) or the centre.
        return lastCandidate != null ? lastCandidate : new Vector3d(centerX + 0.5, y, centerZ + 0.5);
    }
}
