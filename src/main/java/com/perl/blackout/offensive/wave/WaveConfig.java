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
    /** Grace period (seconds) when first entering the instance. */
    public int prepDurationSeconds = 10;
    /** Duration (seconds) of each attack and rest round. */
    public int roundDurationSeconds = 60;
    /** Fixed spawn point used for every player entering the instance. */
    public Position playerSpawn = new Position(20.0, 10.0, 30.0);
    /** The defended crafting machine / objective. */
    public Objective objective = new Objective();
    /** Per-floor enemy configuration. */
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

    public static final class Objective {
        /**
         * NPC role id spawned as the objective. Must be a damageable role (NOT the Seeker, which is
         * invulnerable while peaceful). Defaults to a vulnerable base creature as a placeholder until
         * a dedicated stationary {@code BO_CraftingMachine} role exists.
         */
        public String npcRole = "Cow";
        /** Health applied to the objective on spawn (capped by the role's MaxHealth, e.g. Cow = 103). */
        public float maxHealth = 100.0f;
        /** World position the objective is spawned at. */
        public Position position = new Position(25.0, 10.0, 30.0);
    }

    public static final class Floor {
        public int floor = 0;
        /** Y level enemies spawn at for this floor. */
        public double floorY = 10.0;
        /** Max horizontal radius (blocks) around the objective that enemies spawn within. */
        public double enemySpawnRadius = 24.0;
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
