package com.perl.blackout.offensive.wave;

import javax.annotation.Nonnull;

import org.joml.Vector3d;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

/**
 * Manages the defended objective NPC: spawning it, applying its configurable health, detecting its
 * destruction, and pointing enemies at it.
 */
final class ObjectiveService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** Target slot consumed by NPC roles that pursue a marked entity. */
    private static final String TARGET_SLOT = "LockedTarget";

    /** Spawns the objective NPC and applies the configured health. Must run on the world thread. */
    void spawnObjective(WaveGame game, Store<EntityStore> store, WaveConfig.Objective config) {
        try {
            Vector3d position = new Vector3d(config.position.x, config.position.y, config.position.z);
            var result = NPCPlugin.get().spawnNPC(store, config.npcRole, null, position, new Rotation3f(0.0f, 0.0f, 0.0f));
            Ref<EntityStore> ref = result != null ? result.first() : null;
            if (ref == null) {
                LOGGER.atWarning().log("Failed to spawn objective NPC '%s'", config.npcRole);
                return;
            }
            game.setObjectiveRef(ref);

            EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
            if (stats != null) {
                stats.setStatValue(DefaultEntityStatTypes.getHealth(), config.maxHealth);
            }
            LOGGER.atInfo().log("Spawned objective '%s' with %s HP", config.npcRole, config.maxHealth);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Error spawning objective NPC");
        }
    }

    /** Reads the objective's health; safe to call on the tick thread (read-only). */
    boolean isDestroyed(@Nonnull WaveGame game, @Nonnull Store<EntityStore> store) {
        Ref<EntityStore> ref = game.getObjectiveRef();
        if (ref == null) {
            return false;
        }
        if (!ref.isValid()) {
            return true;
        }
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null) {
            return false;
        }
        EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
        return health == null || health.get() <= 0.0f;
    }

    /**
     * Marks the objective as each enemy's target so roles that support a {@code LockedTarget} slot
     * advance on the machine. No-op for roles (such as the default Seeker) that ignore the slot;
     * those fall back to their normal aggro behaviour. Must run on the world thread.
     */
    void applyTargeting(WaveGame game, Store<EntityStore> store) {
        Ref<EntityStore> objective = game.getObjectiveRef();
        if (objective == null || !objective.isValid()) {
            return;
        }
        for (Ref<EntityStore> enemy : game.getEnemies()) {
            if (enemy == null || !enemy.isValid()) {
                continue;
            }
            NPCEntity npc = store.getComponent(enemy, NPCEntity.getComponentType());
            if (npc == null) {
                continue;
            }
            Role role = npc.getRole();
            if (role != null) {
                try {
                    role.setMarkedTarget(TARGET_SLOT, objective);
                } catch (Exception ignored) {
                    // Role does not expose this target slot; ignore.
                }
            }
        }
    }
}
