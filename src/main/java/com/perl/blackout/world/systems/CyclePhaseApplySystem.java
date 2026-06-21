package com.perl.blackout.world.systems;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class CyclePhaseApplySystem extends WorldEventSystem<EntityStore, BlackoutCycleChangedEvent> {

    public CyclePhaseApplySystem() {
        super(BlackoutCycleChangedEvent.class);
    }

    @Override
    public void handle(@Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull BlackoutCycleChangedEvent event) {
        CyclePhase.applyPhaseToWorld(event.getWorld(), event.isOn());
    }
}
