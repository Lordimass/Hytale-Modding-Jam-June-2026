package com.perl.blackout.offensive.wave;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/**
 * Teleports players to the fixed instance spawn point.
 */
final class PlayerSpawnService {

    /** Teleports the player entity to the given position in the world. Must run on the world thread. */
    void teleport(Ref<EntityStore> playerRef, Store<EntityStore> store, World world, Vector3d position) {
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }
        Teleport teleport = Teleport.createForPlayer(world, new Vector3d(position), new Rotation3f(0.0f, 0.0f, 0.0f));
        store.putComponent(playerRef, Teleport.getComponentType(), teleport);
    }
}
