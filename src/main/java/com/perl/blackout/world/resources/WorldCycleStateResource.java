package com.perl.blackout.world.resources;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class WorldCycleStateResource implements Resource<EntityStore> {

    @Nonnull
    public static final BuilderCodec<WorldCycleStateResource> CODEC =
            BuilderCodec.builder(WorldCycleStateResource.class, WorldCycleStateResource::new)
                    .append(new KeyedCodec<>("On", Codec.BOOLEAN), (resource, value) -> resource.on = value, resource -> resource.on)
                    .add()
                    .build();

    private static ResourceType<EntityStore, WorldCycleStateResource> resourceType;

    public static void setResourceType(ResourceType<EntityStore, WorldCycleStateResource> type) {
        resourceType = type;
    }

    public static ResourceType<EntityStore, WorldCycleStateResource> getResourceType() {
        return resourceType;
    }

    private boolean on;

    public boolean isOn() {
        return this.on;
    }

    public void setOn(boolean on) {
        this.on = on;
    }

    public void toggle() {
        this.on = !this.on;
    }

    @Nonnull
    @Override
    public Resource<EntityStore> clone() {
        WorldCycleStateResource copy = new WorldCycleStateResource();
        copy.on = this.on;
        return copy;
    }
}
