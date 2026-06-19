package com.perl.blackout.offensive.wave;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runtime state for one wave game, tied to a single Backrooms instance world.
 *
 * <p>One game is shared by every player in the instance: they share the round timer. The defended
 * bench is optional — it only exists while a player has a {@code BO_CraftingMachine} placed.
 */
public final class WaveGame {

    private final World world;
    private volatile int floor;

    private volatile WavePhase phase = WavePhase.REST;
    private volatile long phaseStartMs;
    private int round = 0;

    /** Becomes true only once the instance world has loaded and the game has been set up. */
    private volatile boolean initialized = false;

    /** The optional bench NPC (spawned from a placed crafting machine) that enemies prefer to target. */
    @Nullable
    private volatile Ref<EntityStore> benchNpcRef;
    /** Block position of the placed crafting machine backing {@link #benchNpcRef}. */
    @Nullable
    private volatile Vector3i benchBlockPos;

    private final List<Ref<EntityStore>> enemies = Collections.synchronizedList(new ArrayList<>());
    private final List<Ref<EntityStore>> persistentEnemies = Collections.synchronizedList(new ArrayList<>());

    public WaveGame(World world, int floor) {
        this.world = world;
        this.floor = floor;
        this.phaseStartMs = System.currentTimeMillis();
    }

    public World getWorld() {
        return world;
    }

    public int getFloor() {
        return floor;
    }

    public void setFloor(int floor) {
        this.floor = floor;
    }

    public WavePhase getPhase() {
        return phase;
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

    // ── Bench (optional defended objective) ──

    @Nullable
    public Ref<EntityStore> getBenchNpcRef() {
        return benchNpcRef;
    }

    @Nullable
    public Vector3i getBenchBlockPos() {
        return benchBlockPos;
    }

    public boolean hasBench() {
        return benchNpcRef != null;
    }

    public void setBench(Ref<EntityStore> npcRef, Vector3i blockPos) {
        this.benchNpcRef = npcRef;
        this.benchBlockPos = blockPos;
    }

    public void clearBench() {
        this.benchNpcRef = null;
        this.benchBlockPos = null;
    }

    // ── Enemies ──

    public void addEnemy(Ref<EntityStore> enemy) {
        enemies.add(enemy);
    }

    public void addPersistentEnemy(Ref<EntityStore> enemy) {
        persistentEnemies.add(enemy);
    }

    public List<Ref<EntityStore>> getEnemies() {
        return enemies;
    }

    public List<Ref<EntityStore>> getPersistentEnemies() {
        return persistentEnemies;
    }

    public List<Ref<EntityStore>> getAllEnemiesSnapshot() {
        List<Ref<EntityStore>> all = new ArrayList<>();
        synchronized (enemies) {
            all.addAll(enemies);
        }
        synchronized (persistentEnemies) {
            all.addAll(persistentEnemies);
        }
        return all;
    }

    public void clearEnemies() {
        enemies.clear();
    }

    public void clearPersistentEnemies() {
        persistentEnemies.clear();
    }
}
