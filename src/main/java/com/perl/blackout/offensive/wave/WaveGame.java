package com.perl.blackout.offensive.wave;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runtime state for one wave game, tied to a single Backrooms instance world.
 *
 * <p>One game is shared by every player in the instance: they share the round timer and the
 * single defended objective.
 */
public final class WaveGame {

    private final World world;
    private final float objectiveMaxHealth;
    private final int floor;

    private volatile WavePhase phase = WavePhase.PREP;
    private volatile long phaseStartMs;
    private int round = 0;

    /** Becomes true only once the instance world has loaded and the objective + spawn are set up. */
    private volatile boolean initialized = false;

    @Nullable
    private volatile Vector3d playerSpawn;
    @Nullable
    private volatile Ref<EntityStore> objectiveRef;

    private final List<Ref<EntityStore>> enemies = Collections.synchronizedList(new ArrayList<>());

    public WaveGame(World world, float objectiveMaxHealth, int floor) {
        this.world = world;
        this.objectiveMaxHealth = objectiveMaxHealth;
        this.floor = floor;
        this.phaseStartMs = System.currentTimeMillis();
    }

    public World getWorld() {
        return world;
    }

    public float getObjectiveMaxHealth() {
        return objectiveMaxHealth;
    }

    public int getFloor() {
        return floor;
    }

    public WavePhase getPhase() {
        return phase;
    }

    public long getPhaseStartMs() {
        return phaseStartMs;
    }

    public long getPhaseElapsedMs(long now) {
        return now - phaseStartMs;
    }

    public void startPhase(WavePhase phase, long now) {
        this.phase = phase;
        this.phaseStartMs = now;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public int getRound() {
        return round;
    }

    public int incrementRound() {
        return ++round;
    }

    @Nullable
    public Vector3d getPlayerSpawn() {
        return playerSpawn;
    }

    public void setPlayerSpawn(Vector3d playerSpawn) {
        this.playerSpawn = playerSpawn;
    }

    @Nullable
    public Ref<EntityStore> getObjectiveRef() {
        return objectiveRef;
    }

    public void setObjectiveRef(Ref<EntityStore> objectiveRef) {
        this.objectiveRef = objectiveRef;
    }

    public boolean isObjectiveSpawned() {
        return objectiveRef != null;
    }

    public void addEnemy(Ref<EntityStore> enemy) {
        enemies.add(enemy);
    }

    public List<Ref<EntityStore>> getEnemies() {
        return enemies;
    }

    public void clearEnemies() {
        enemies.clear();
    }
}
