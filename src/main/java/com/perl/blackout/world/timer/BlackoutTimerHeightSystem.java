package com.perl.blackout.world.timer;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class BlackoutTimerHeightSystem extends EntityTickingSystem<EntityStore> {

    @Nonnull
    private static final ComponentType<EntityStore, PlayerRef> PLAYER_REF = PlayerRef.getComponentType();
    @Nonnull
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM = TransformComponent.getComponentType();
    @Nonnull
    private final BlackoutTimerService timerService;
    @Nonnull
    private final Query<EntityStore> query = Query.and(PLAYER_REF, TRANSFORM);

    public BlackoutTimerHeightSystem(@Nonnull BlackoutTimerService timerService) {
        this.timerService = timerService;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    @Override
    public void tick(float dt,
                     int index,
                     @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (!BlackoutTimerService.isBackroomsWorld(store.getExternalData().getWorld())) {
            return;
        }

        TransformComponent transform = archetypeChunk.getComponent(index, TRANSFORM);
        if (transform == null) {
            return;
        }

        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        this.timerService.markPoolClearedIfReached(store, playerRef, transform.getPosition().y());
    }
}
