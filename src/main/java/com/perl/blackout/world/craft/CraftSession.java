package com.perl.blackout.world.craft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Runtime state for one active craft altar session, bound to a single world.
 *
 * <p>Fields accessed from both the tick thread and the world thread are volatile.
 */
public final class CraftSession {

    /** Total craft duration: 10 minutes. */
    public static final long CRAFT_DURATION_MS = 10L * 60L * 1000L;

    private final World world;
    private final Vector3d position;
    private final long startMs;

    /** The NPC entity spawned at the altar position — what enemies target and attack. */
    @Nullable
    private volatile Ref<EntityStore> altarNpcRef;

    /** Live seeker refs maintained by the continuous spawn system. Modified on world thread only. */
    private final List<Ref<EntityStore>> seekers = Collections.synchronizedList(new ArrayList<>());

    /** Timestamp of the last spawn-topup dispatch; read/written on the tick thread. */
    private volatile long lastSpawnDispatchMs;

    CraftSession(World world, Vector3d position) {
        this.world = world;
        this.position = position;
        this.startMs = System.currentTimeMillis();
    }

    World getWorld() {
        return world;
    }

    Vector3d getPosition() {
        return position;
    }

    long getStartMs() {
        return startMs;
    }

    @Nullable
    Ref<EntityStore> getAltarNpcRef() {
        return altarNpcRef;
    }

    void setAltarNpcRef(Ref<EntityStore> ref) {
        this.altarNpcRef = ref;
    }

    List<Ref<EntityStore>> getSeekers() {
        return seekers;
    }

    void addSeeker(Ref<EntityStore> ref) {
        seekers.add(ref);
    }

    long getLastSpawnDispatchMs() {
        return lastSpawnDispatchMs;
    }

    void setLastSpawnDispatchMs(long ms) {
        this.lastSpawnDispatchMs = ms;
    }

    /** Milliseconds remaining until the craft completes (clamped to 0). */
    long getRemainingMs() {
        return Math.max(0L, CRAFT_DURATION_MS - (System.currentTimeMillis() - startMs));
    }

    boolean isComplete() {
        return System.currentTimeMillis() - startMs >= CRAFT_DURATION_MS;
    }
}
