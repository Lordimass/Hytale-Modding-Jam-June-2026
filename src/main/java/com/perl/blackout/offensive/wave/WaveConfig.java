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

    /** Substring matched case-insensitively against a world's name to detect a Backrooms instance. */
    public String instanceWorldMatch = "Backrooms";
    /** Daytime rest period (seconds): the grace on entry and the lull between attack waves. */
    public int restDurationSeconds = 30;
    /** Night-time attack period (seconds): how long each wave of enemies hunts before daybreak. */
    public int attackDurationSeconds = 60;
    /** Horizontal distance (blocks) from a player that enemies spawn at the start of an attack. */
    public double enemySpawnDistance = 20.0;
    /** Random +/- variation (blocks) applied to {@link #enemySpawnDistance} per enemy. */
    public double enemySpawnDistanceJitter = 6.0;
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
        public List<EnemyGroup> enemies = new ArrayList<>();
    }

    public static final class EnemyGroup {
        public String type = "Seeker";
        public int count = 6;

        public EnemyGroup() {
        }

        public EnemyGroup(String type, int count) {
            this.type = type;
            this.count = count;
        }
    }

    private static List<Floor> defaultFloors() {
        Floor floor0 = new Floor();
        floor0.enemies.add(new EnemyGroup("Seeker", 6));
        List<Floor> list = new ArrayList<>();
        list.add(floor0);
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
            return config != null ? config : new WaveConfig();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to load wave config; using defaults");
            return new WaveConfig();
        }
    }
}
