package com.perl.blackout.world.craft;

import javax.annotation.Nonnull;

import org.joml.Vector3i;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.perl.blackout.offensive.OffensivePlugin;
import com.perl.blackout.offensive.wave.WaveGameManager;

/**
 * Listens for a player breaking wave-relevant blocks and removes their hidden NPC targets. The
 * block break itself handles normal item returns.
 */
public final class CraftAltarBreakHandler extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public CraftAltarBreakHandler() {
        super(BreakBlockEvent.class);
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull BreakBlockEvent event) {
        BlockType blockType = event.getBlockType();
        if (blockType == null || !isWaveBlock(blockType.getId())) {
            return;
        }

        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        if (player == null) {
            return;
        }

        World world = player.getWorld();
        if (world == null) {
            return;
        }

        WaveGameManager manager = waveManager();
        if (manager == null) {
            return;
        }

        Vector3i blockPos = new Vector3i(event.getTargetBlock());
        if (WaveGameManager.BENCH_BLOCK_ID.equals(blockType.getId())) {
            manager.onBenchBroken(world, blockPos);
        } else if (isFenceBlock(blockType.getId())) {
            manager.onFenceBroken(world, blockPos);
        }
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    private static WaveGameManager waveManager() {
        OffensivePlugin plugin = OffensivePlugin.getInstance();
        return plugin != null ? plugin.getWaveGameManager() : null;
    }

    private boolean isWaveBlock(String blockId) {
        return WaveGameManager.BENCH_BLOCK_ID.equals(blockId) || isFenceBlock(blockId);
    }

    private boolean isFenceBlock(String blockId) {
        return WaveGameManager.FENCE_BLOCK_ID.equals(blockId)
                || blockId.startsWith(WaveGameManager.FENCE_BLOCK_ID + "_");
    }
}
