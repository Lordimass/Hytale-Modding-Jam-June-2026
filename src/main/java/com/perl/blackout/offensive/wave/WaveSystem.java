package com.perl.blackout.offensive.wave;

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
import com.perl.blackout.offensive.OffensivePlugin;

/**
 * The wave game loop. Runs every server tick for each player; advances the wave game for the
 * player's world on a throttled cadence. The actual round timing comes from {@link WaveGame}'s
 * phase clock, not from this throttle.
 */
public final class WaveSystem extends EntityTickingSystem<EntityStore> {

    /** How often shared per-world wave logic runs (the 60s round clock lives in {@link WaveGame}). */
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

        OffensivePlugin plugin = OffensivePlugin.getInstance();
        if (plugin == null) {
            return;
        }
        WaveGameManager manager = plugin.getWaveGameManager();
        if (manager == null || manager.getGame(world) == null) {
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
