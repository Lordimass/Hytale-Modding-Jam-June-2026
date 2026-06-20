package com.perl.blackout.offensive.wave;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

/**
 * JSON-backed configuration for the wave system, loaded from the Offensive plugin's data
 * directory ({@code waves.json}). A default file is written on first run.
 *
 * <p>Fields are public and use sensible defaults so missing JSON keys fall back gracefully.
 */
public final class WaveConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "waves.json";
    private static final String WATCHED_DUMMY_ROLE = "BO_WatchedDummy";

    /** Substring matched case-insensitively against a world's name to detect a Backrooms instance. */
    public String instanceWorldMatch = "Backrooms";
    /** Daytime rest period (seconds): the grace on entry and the lull between attack waves. */
    public int restDurationSeconds = 60;
    /** Night-time attack period (seconds): how long each wave of enemies hunts before daybreak. */
    public int attackDurationSeconds = 60;
    /** Horizontal distance (blocks) from a player that enemies spawn at the start of an attack. */
    public double enemySpawnDistance = 20.0;
    /** Random +/- variation (blocks) applied to {@link #enemySpawnDistance} per enemy. */
    public double enemySpawnDistanceJitter = 6.0;
    /** Minimum distance from each player for persistent enemies that are maintained around them. */
    public double persistentEnemySpawnMinDistance = 24.0;
    /** Maximum distance from each player for persistent enemies that are maintained around them. */
    public double persistentEnemySpawnMaxDistance = 64.0;
    /** Distance used to decide if enough persistent enemies are close to a player. */
    public double persistentEnemyNearbyDistance = 72.0;
    /** Persistent enemies farther than this from every player are despawned and replaced nearby. */
    public double persistentEnemyDespawnDistance = 96.0;
    /**
     * Fallback anchor used only to preload a chunk while the instance finishes loading, and to
     * place enemies when no players can be located. Real spawns are relative to players.
     */
    public Position playerSpawn = new Position(20.0, 10.0, 30.0);
    /** Per-floor enemy configuration. The floor (level) is the future progression axis for the Key. */
    public List<Floor> floors = defaultFloors();

    public static final class Position {
        public double x;
        public double y;
        public double z;

        public Position() {
        }

        public Position(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static final class Floor {
        public int floor = 0;
        /** Y level used for enemy spawns only as a fallback when no player position is available. */
        public double floorY = 10.0;
        /** True for enemies that should survive daybreak and only be replaced after death. */
        public boolean persistentEnemies = false;
        /** True when this floor should seed its persistent enemies as soon as the instance starts. */
        public boolean spawnEnemiesOnInitialize = false;
        public List<EnemyGroup> enemies = new ArrayList<>();

        public boolean hazardEnabled = false;
        public double hazardYMin = 0.0;
        public double hazardYMax = 0.0;
        public int hazardEffectStartSeconds = 20;
        public int hazardKillStartSeconds = 30;
        public String hazardEffectId = "BO_Garage_Exposure";

        public boolean hasHazard() {
            return hazardEnabled && hazardEffectId != null && !hazardEffectId.isBlank() && hazardYMax > hazardYMin;
        }
    }

    public static final class EnemyGroup {
        public String type = "Seeker";
        public int count = 6;
        /** True for this specific enemy type when it should survive daybreak. */
        public boolean persistent = false;
        /** True when this persistent enemy type should seed as soon as the instance starts. */
        public boolean spawnOnInitialize = false;

        public EnemyGroup() {
        }

        public EnemyGroup(String type, int count) {
            this.type = type;
            this.count = count;
        }

        public EnemyGroup(String type, int count, boolean persistent, boolean spawnOnInitialize) {
            this.type = type;
            this.count = count;
            this.persistent = persistent;
            this.spawnOnInitialize = spawnOnInitialize;
        }
    }

    private static List<Floor> defaultFloors() {
        Floor floor0 = new Floor();
        floor0.floor = 0;
        floor0.floorY = 23.0;
        floor0.spawnEnemiesOnInitialize = true;
        floor0.enemies.add(new EnemyGroup("Seeker", 6));
        floor0.enemies.add(new EnemyGroup("BO_WatchedDummy", 4, true, true));

        Floor floor1 = new Floor();
        floor1.floor = 1;
        floor1.floorY = 33.5;

        Floor floor2 = new Floor();
        floor2.floor = 2;
        floor2.floorY = 4.0;
        floor2.enemies.add(new EnemyGroup("Seeker", 6));
        floor2.hazardEnabled = true;
        floor2.hazardYMin = 0.0;
        floor2.hazardYMax = 16.0;

        List<Floor> list = new ArrayList<>();
        list.add(floor0);
        list.add(floor1);
        list.add(floor2);
        return list;
    }

    @Nullable
    public Floor findFloor(int floor) {
        for (Floor candidate : floors) {
            if (candidate.floor == floor) {
                return candidate;
            }
        }
        return null;
    }

    @Nullable
    public Floor findHazardFloor() {
        for (Floor candidate : floors) {
            if (candidate.hasHazard()) {
                return candidate;
            }
        }
        return null;
    }

    /** Ensures {@code waves.json} exists in the data directory (writing defaults if missing) and returns its path. */
    public static Path ensureRuntimeConfig(Path dataDirectory) {
        Path path = dataDirectory.resolve(FILE_NAME);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(dataDirectory);
                Files.writeString(path, GSON.toJson(new WaveConfig()), StandardCharsets.UTF_8);
                LOGGER.atInfo().log("Wrote default wave config to %s", path);
            } catch (IOException e) {
                LOGGER.atWarning().withCause(e).log("Failed to write default wave config");
            }
        }
        return path;
    }

    /** Loads the config from disk, falling back to defaults on any error. */
    public static WaveConfig load(Path path) {
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            WaveConfig config = GSON.fromJson(reader, WaveConfig.class);
            if (config == null) {
                return new WaveConfig();
            }
            config.normalize();
            return config;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to load wave config; using defaults");
            return new WaveConfig();
        }
    }

    private void normalize() {
        if (floors == null || floors.isEmpty() || isLegacyDefaultFloorList(floors)) {
            floors = defaultFloors();
            return;
        }
        for (Floor floor : floors) {
            if (floor.enemies == null) {
                floor.enemies = new ArrayList<>();
            }
            if (floor.persistentEnemies) {
                for (EnemyGroup enemy : floor.enemies) {
                    enemy.persistent = true;
                    enemy.spawnOnInitialize = floor.spawnEnemiesOnInitialize;
                }
            }
            if (floor.floor == 0) {
                ensureWatchedDummyGroup(floor);
            }
        }
    }

    private static void ensureWatchedDummyGroup(Floor floor) {
        for (EnemyGroup enemy : floor.enemies) {
            if (enemy != null && WATCHED_DUMMY_ROLE.equals(enemy.type)) {
                enemy.persistent = true;
                enemy.spawnOnInitialize = true;
                if (enemy.count <= 0) {
                    enemy.count = 4;
                }
                return;
            }
        }
        floor.enemies.add(new EnemyGroup(WATCHED_DUMMY_ROLE, 4, true, true));
    }

    private static boolean isLegacyDefaultFloorList(List<Floor> floors) {
        if (floors.size() != 1) {
            return false;
        }
        Floor floor = floors.get(0);
        if (floor == null || floor.floor != 0 || floor.persistentEnemies || floor.spawnEnemiesOnInitialize
                || Math.abs(floor.floorY - 10.0) > 0.0001 || floor.enemies == null || floor.enemies.size() != 1) {
            return false;
        }
        EnemyGroup enemy = floor.enemies.get(0);
        return enemy != null && "Seeker".equals(enemy.type) && enemy.count == 6;
    }
}
