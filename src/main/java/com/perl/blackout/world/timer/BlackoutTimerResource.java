package com.perl.blackout.world.timer;

import java.time.Instant;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class BlackoutTimerResource implements Resource<EntityStore> {

    @Nonnull
    public static final BuilderCodec<BlackoutTimerResource> CODEC =
            BuilderCodec.builder(BlackoutTimerResource.class, BlackoutTimerResource::new)
                    .append(new KeyedCodec<>("StartedAt", Codec.INSTANT),
                            (resource, value) -> resource.startedAt = value, resource -> resource.startedAt)
                    .add()
                    .append(new KeyedCodec<>("OfficeFloorClearedAt", Codec.INSTANT),
                            (resource, value) -> resource.officeFloorClearedAt = value,
                            resource -> resource.officeFloorClearedAt)
                    .add()
                    .append(new KeyedCodec<>("PoolFloorClearedAt", Codec.INSTANT),
                            (resource, value) -> resource.poolFloorClearedAt = value,
                            resource -> resource.poolFloorClearedAt)
                    .add()
                    .append(new KeyedCodec<>("GarageClearedAt", Codec.INSTANT),
                            (resource, value) -> resource.garageClearedAt = value,
                            resource -> resource.garageClearedAt)
                    .add()
                    .build();

    private static ResourceType<EntityStore, BlackoutTimerResource> resourceType;

    @Nullable
    private Instant startedAt;
    @Nullable
    private Instant officeFloorClearedAt;
    @Nullable
    private Instant poolFloorClearedAt;
    @Nullable
    private Instant garageClearedAt;

    public static void setResourceType(ResourceType<EntityStore, BlackoutTimerResource> type) {
        resourceType = type;
    }

    public static ResourceType<EntityStore, BlackoutTimerResource> getResourceType() {
        return resourceType;
    }

    public boolean isRunning() {
        return this.startedAt != null && this.garageClearedAt == null;
    }

    public void reset() {
        this.startedAt = null;
        this.officeFloorClearedAt = null;
        this.poolFloorClearedAt = null;
        this.garageClearedAt = null;
    }

    public void start(@Nonnull Instant startedAt) {
        this.startedAt = startedAt;
        this.officeFloorClearedAt = null;
        this.poolFloorClearedAt = null;
        this.garageClearedAt = null;
    }

    @Nullable
    public Instant getStartedAt() {
        return this.startedAt;
    }

    @Nullable
    public Instant getOfficeFloorClearedAt() {
        return this.officeFloorClearedAt;
    }

    public void setOfficeFloorClearedAt(@Nonnull Instant officeFloorClearedAt) {
        this.officeFloorClearedAt = officeFloorClearedAt;
    }

    @Nullable
    public Instant getPoolFloorClearedAt() {
        return this.poolFloorClearedAt;
    }

    public void setPoolFloorClearedAt(@Nonnull Instant poolFloorClearedAt) {
        this.poolFloorClearedAt = poolFloorClearedAt;
    }

    @Nullable
    public Instant getGarageClearedAt() {
        return this.garageClearedAt;
    }

    public void setGarageClearedAt(@Nonnull Instant garageClearedAt) {
        this.garageClearedAt = garageClearedAt;
    }

    @Nonnull
    @Override
    public Resource<EntityStore> clone() {
        BlackoutTimerResource copy = new BlackoutTimerResource();
        copy.startedAt = this.startedAt;
        copy.officeFloorClearedAt = this.officeFloorClearedAt;
        copy.poolFloorClearedAt = this.poolFloorClearedAt;
        copy.garageClearedAt = this.garageClearedAt;
        return copy;
    }
}
