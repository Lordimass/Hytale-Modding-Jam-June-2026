package com.perl.blackout.world.systems;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.DelayedSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.perl.blackout.world.resources.WorldCycleStateResource;

public final class WorldCycleDriverSystem extends DelayedSystem<EntityStore> {

    private static final float CYCLE_INTERVAL_SECONDS = 300.0F;

    public WorldCycleDriverSystem() {
        super(CYCLE_INTERVAL_SECONDS);
    }

    @Override
    public void delayedTick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        WorldCycleStateResource state = store.getResource(WorldCycleStateResource.getResourceType());
        if (state == null) {
            return;
        }
        state.toggle();
        CyclePhase.applyPhaseToWorld(store.getExternalData().getWorld(), state.isOn());
    }
}
