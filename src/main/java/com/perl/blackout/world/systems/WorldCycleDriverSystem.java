package com.perl.blackout.world.systems;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.DelayedSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.perl.blackout.offensive.OffensivePlugin;
import com.perl.blackout.offensive.wave.WaveGameManager;
import com.perl.blackout.world.resources.WorldCycleStateResource;

/**
 * Automatic day/night blackout cycle for ordinary worlds.
 *
 * <p>Worlds running a wave game (the Backrooms instance) are skipped: there the {@link WaveGameManager}
 * is the sole authority over the blackout phase, driving it from the attack/rest timers. Without this
 * guard the two systems fight — the 5-minute auto-toggle flips the lights out from under the wave loop.
 */
public final class WorldCycleDriverSystem extends DelayedSystem<EntityStore> {

    private static final float CYCLE_INTERVAL_SECONDS = 300.0F;

    public WorldCycleDriverSystem() {
        super(CYCLE_INTERVAL_SECONDS);
    }

    @Override
    public void delayedTick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        if (isDrivenByWaveGame(world)) {
            return;
        }

        WorldCycleStateResource state = store.getResource(WorldCycleStateResource.getResourceType());
        if (state == null) {
            return;
        }
        state.toggle();
        CyclePhase.applyPhaseToWorld(world, state.isOn());
    }

    /** True if a wave game owns this world's blackout phase, in which case the auto-cycle must stand down. */
    private static boolean isDrivenByWaveGame(@Nonnull World world) {
        OffensivePlugin offensive = OffensivePlugin.getInstance();
        if (offensive == null) {
            return false;
        }
        WaveGameManager waves = offensive.getWaveGameManager();
        return waves != null && waves.getGame(world) != null;
    }
}
