package com.perl.blackout.offensive.wave;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for locating the players currently inside an instance world.
 *
 * <p>Resolving a {@link PlayerRef} to its entity transform must happen on the world thread.
 */
final class WavePlayers {

    private WavePlayers() {
    }

    /** Live player entity refs in the world. */
    static List<Ref<EntityStore>> refs(World world) {
        List<Ref<EntityStore>> out = new ArrayList<>();
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref != null && ref.isValid()) {
                out.add(ref);
            }
        }
        return out;
    }

    /** Current positions of all players in the world. */
    static List<Vector3d> positions(World world, Store<EntityStore> store) {
        List<Vector3d> out = new ArrayList<>();
        for (Ref<EntityStore> ref : refs(world)) {
            Vector3d pos = positionOf(store, ref);
            if (pos != null) {
                out.add(pos);
            }
        }
        return out;
    }

    @Nullable
    static Vector3d positionOf(Store<EntityStore> store, Ref<EntityStore> ref) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        return transform != null ? new Vector3d(transform.getPosition()) : null;
    }
}
