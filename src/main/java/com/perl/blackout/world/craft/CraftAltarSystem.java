package com.perl.blackout.world.craft;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Drives the craft altar timer. Runs on every server tick for each player entity,
 * throttled to avoid redundant per-world processing. Delegates to {@link CraftAltarManager}.
 */
public final class CraftAltarSystem extends EntityTickingSystem<EntityStore> {

    private static final long ADVANCE_INTERVAL_MS = 500L;

    private final Map<World, Long> lastAdvanceByWorld = new ConcurrentHashMap<>();

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (!ref.isValid()) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        World world = player.getWorld();
        if (world == null) {
            return;
        }

        CraftAltarManager manager = CraftAltarManager.getInstance();
        if (manager.getSession(world) == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastAdvanceByWorld.get(world);
        if (last != null && now - last < ADVANCE_INTERVAL_MS) {
            return;
        }
        lastAdvanceByWorld.put(world, now);

        manager.advance(world, store, now);
    }
}
