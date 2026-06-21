package com.perl.blackout.world.systems;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class CycleTimeSetSystem extends WorldEventSystem<EntityStore, BlackoutCycleChangedEvent> {

    private static final double NIGHT_DAY_TIME = 0.0;
    private static final double DAY_DAY_TIME = 0.5;

    public CycleTimeSetSystem() {
        super(BlackoutCycleChangedEvent.class);
    }

    @Override
    public void handle(@Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull BlackoutCycleChangedEvent event) {
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        if (time == null) {
            return;
        }
        time.setDayTime(event.isOn() ? DAY_DAY_TIME : NIGHT_DAY_TIME, event.getWorld(), store);
    }
}
