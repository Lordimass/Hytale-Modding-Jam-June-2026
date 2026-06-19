package com.perl.blackout.world.systems;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.perl.blackout.world.components.CycelStateComponent;

public final class CyclePhase {

    private CyclePhase() {
    }

    public static void applyPhaseToWorld(@Nonnull World world, boolean on) {
        Store<ChunkStore> store = world.getChunkStore().getStore();
        Query<ChunkStore> query = Query.and(CycelStateComponent.getComponentType(), BlockStateInfo.getComponentType());
        store.forEachChunk(query, (archetypeChunk, commandBuffer) -> {
            for (int i = 0; i < archetypeChunk.size(); i++) {
                CycelStateComponent component = archetypeChunk.getComponent(i, CycelStateComponent.getComponentType());
                BlockStateInfo blockStateInfo = archetypeChunk.getComponent(i, BlockStateInfo.getComponentType());
                if (component == null || blockStateInfo == null) {
                    continue;
                }
                apply(world, commandBuffer, blockStateInfo.getChunkRef(), blockStateInfo.getIndex(),
                        component.stateFor(on), component.soundIndexFor(on));
            }
        });
    }

    static void applyState(@Nonnull ComponentAccessor<ChunkStore> accessor,
            @Nonnull Ref<ChunkStore> chunkRef,
            int blockIndex,
            @Nullable String stateName) {
        apply(null, accessor, chunkRef, blockIndex, stateName, 0);
    }

    private static void apply(@Nullable World world,
            @Nonnull ComponentAccessor<ChunkStore> accessor,
            @Nonnull Ref<ChunkStore> chunkRef,
            int blockIndex,
            @Nullable String stateName,
            int soundIndex) {
        if (!chunkRef.isValid()) {
            return;
        }

        WorldChunk worldChunk = accessor.getComponent(chunkRef, WorldChunk.getComponentType());
        if (worldChunk == null) {
            return;
        }

        int x = ChunkUtil.xFromBlockInColumn(blockIndex);
        int y = ChunkUtil.yFromBlockInColumn(blockIndex);
        int z = ChunkUtil.zFromBlockInColumn(blockIndex);

        if (stateName != null && !stateName.isEmpty()) {
            BlockType blockType = worldChunk.getBlockType(x, y, z);
            if (blockType != null) {
                worldChunk.setBlockInteractionState(x, y, z, blockType, stateName, false);
            }
        }

        if (world != null && soundIndex > 0) {
            double worldX = ((worldChunk.getX() << 5) | x) + 0.5;
            double worldZ = ((worldChunk.getZ() << 5) | z) + 0.5;
            SoundUtil.playSoundEvent3d(soundIndex, SoundCategory.SFX, worldX, y + 0.5, worldZ,
                    world.getEntityStore().getStore());
        }
    }
}
