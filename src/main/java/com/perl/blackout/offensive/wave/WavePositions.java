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

    /** A position is open if its chunk is already in memory and the block at it and above are passable. */
    static boolean isOpen(World world, int x, int y, int z) {
        WorldChunk chunk = world.loadChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
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
        int yi = (int) Math.floor(y);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = random.nextDouble() * radius;
            int x = (int) Math.floor(centerX + Math.cos(angle) * dist);
            int z = (int) Math.floor(centerZ + Math.sin(angle) * dist);
            if (isOpen(world, x, yi, z)) {
                return new Vector3d(x + 0.5, y, z + 0.5);
            }   
        }
        return new Vector3d(centerX + 0.5, y, centerZ + 0.5);
    }
}
