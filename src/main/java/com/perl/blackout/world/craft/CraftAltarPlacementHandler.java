package com.perl.blackout.world.craft;

import javax.annotation.Nonnull;

import org.joml.Vector3d;
import org.joml.Vector3i;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Listens for the altar block being placed and hands off to {@link CraftAltarManager}.
 *
 * <p>Fires on the tick thread; all entity mutations are deferred to the world thread
 * via {@code world.execute(...)}.
 */
public final class CraftAltarPlacementHandler extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    public CraftAltarPlacementHandler() {
        super(PlaceBlockEvent.class);
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull PlaceBlockEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || !CraftAltarManager.ALTAR_BLOCK_ID.equals(item.getItemId())) {
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

        // getTargetBlock() returns the position of the block being placed.
        Vector3i blockPos = event.getTargetBlock();
        // Centre the NPC on the block horizontally; keep floor Y.
        Vector3d spawnPos = new Vector3d(blockPos.x + 0.5, blockPos.y, blockPos.z + 0.5);

        world.execute(() -> {
            Store<EntityStore> worldStore = world.getEntityStore().getStore();
            CraftAltarManager.getInstance().onAltarPlaced(world, spawnPos, worldStore);
        });
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }
}
