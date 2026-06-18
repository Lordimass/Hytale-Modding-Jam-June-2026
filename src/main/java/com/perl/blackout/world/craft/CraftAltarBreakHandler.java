package com.perl.blackout.world.craft;

import javax.annotation.Nonnull;

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
        if (blockType == null) return;
        if (!CraftAltarManager.ALTAR_BLOCK_ID.equals(blockType.getId())) return;

        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        if (player == null) return;

        World world = player.getWorld();
        if (world == null) return;

        CraftAltarManager.getInstance().onAltarBlockBroken(world);
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }
}
