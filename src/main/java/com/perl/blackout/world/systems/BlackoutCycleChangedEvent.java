package com.perl.blackout.world.systems;

import com.hypixel.hytale.component.system.EcsEvent;
import com.hypixel.hytale.server.core.universe.world.World;

public final class BlackoutCycleChangedEvent extends EcsEvent {

    private final World world;
    private final boolean on;

    public BlackoutCycleChangedEvent(World world, boolean on) {
        this.world = world;
        this.on = on;
    }

    public World getWorld() {
        return this.world;
    }

    public boolean isOn() {
        return this.on;
    }
}
