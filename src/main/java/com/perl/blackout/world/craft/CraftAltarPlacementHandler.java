package com.perl.blackout.world.craft;

import javax.annotation.Nonnull;

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
import com.perl.blackout.offensive.OffensivePlugin;
import com.perl.blackout.offensive.wave.WaveGameManager;

/**
 * Listens for the crafting machine block being placed and registers it with the wave game as the
 * optional bench the enemies prefer to attack. The block itself remains a normal (native) crafting
 * bench; this only spawns the bench NPC target.
 *
 * <p>Fires on the tick thread; the wave manager defers entity mutations to the world thread.
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
        if (item == null || !WaveGameManager.BENCH_BLOCK_ID.equals(item.getItemId())) {
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
        manager.onBenchPlaced(world, blockPos);
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
}
