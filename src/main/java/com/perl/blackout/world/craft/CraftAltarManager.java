package com.perl.blackout.world.craft;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.perl.blackout.offensive.wave.WavePositions;

public final class CraftAltarManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String ALTAR_BLOCK_ID = "BO_CraftingMachine";
    public static final String ALTAR_NPC_ROLE = "BO_Altar";
    public static final float ALTAR_MAX_HEALTH = 200.0f;

    private static final String SEEKER_ROLE = "Seeker";
    private static final int MAX_SEEKERS = 20;
    private static final double SPAWN_RADIUS = 50.0;
    private static final long SPAWN_INTERVAL_MS = 2_000L;

    private static final String TARGET_SLOT = "LockedTarget";
    private static final double TIME_MIDNIGHT = 0.0;
    private static final long BANNER_INTERVAL_MS = 30_000L;

    @Nullable
    private static CraftAltarManager instance;

    private final Map<World, CraftSession> sessions = new ConcurrentHashMap<>();
    private final Map<World, Long> lastBannerMs = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private CraftAltarManager() {}

    public static CraftAltarManager getInstance() {
        if (instance == null) {
            instance = new CraftAltarManager();
        }
        return instance;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void onAltarPlaced(World world, Vector3d position, Store<EntityStore> store) {
        if (sessions.containsKey(world)) {
            LOGGER.atInfo().log("Craft altar already active in world '%s'; ignoring duplicate placement",
                    world.getName());
            return;
        }

        CraftSession session = new CraftSession(world, position);
        sessions.put(world, session);

        world.getWorldConfig().setGameTimePaused(true);
        setTime(world, store, TIME_MIDNIGHT);

        spawnAltarNpc(session, store);

        broadcast(world, "Craft Begun!", "Defend the machine! Craft completes in 10:00.", true);
        lastBannerMs.put(world, System.currentTimeMillis());

        LOGGER.atInfo().log("Craft altar session started in world '%s' at %s", world.getName(), position);
    }

    public void advance(World world, Store<EntityStore> store, long now) {
        CraftSession session = sessions.get(world);
        if (session == null) return;

        if (isAltarDestroyed(session, store)) {
            broadcast(world, "Craft Destroyed!", "The altar was destroyed — craft failed.", true);
            endSession(world, session, true);
            LOGGER.atInfo().log("Craft altar destroyed in world '%s'", world.getName());
            return;
        }

        if (session.isComplete()) {
            broadcast(world, "Craft Complete!", "The machine has finished crafting!", true);
            endSession(world, session, false);
            LOGGER.atInfo().log("Craft completed in world '%s'", world.getName());
            return;
        }

        // Maintain seeker population: dispatch topup to world thread every SPAWN_INTERVAL_MS.
        if (now - session.getLastSpawnDispatchMs() >= SPAWN_INTERVAL_MS) {
            session.setLastSpawnDispatchMs(now);
            world.execute(() -> topUpSeekers(session, world));
        }

        Long lastBanner = lastBannerMs.get(world);
        if (lastBanner == null || now - lastBanner >= BANNER_INTERVAL_MS) {
            lastBannerMs.put(world, now);
            broadcastTimer(world, session);
        }
    }

    public void onAltarBlockBroken(World world) {
        CraftSession session = sessions.get(world);
        if (session == null) return;
        broadcast(world, "Craft Destroyed!", "The altar block was broken — craft failed.", true);
        endSession(world, session, true);
        LOGGER.atInfo().log("Craft altar block broken in world '%s'", world.getName());
    }

    @Nullable
    public CraftSession getSession(World world) {
        return sessions.get(world);
    }

    public void onWorldRemoved(World world) {
        sessions.remove(world);
        lastBannerMs.remove(world);
    }

    public void shutdown() {
        sessions.clear();
        lastBannerMs.clear();
        instance = null;
    }

    // ── Seeker spawn system ───────────────────────────────────────────────────

    /** Prunes dead refs, then spawns seekers up to MAX_SEEKERS, each targeted at the altar. World thread. */
    private void topUpSeekers(CraftSession session, World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        List<Ref<EntityStore>> seekers = session.getSeekers();

        synchronized (seekers) {
            seekers.removeIf(ref -> ref == null || !ref.isValid());
        }

        int toSpawn = MAX_SEEKERS - seekers.size();
        if (toSpawn <= 0) return;

        Vector3d altar = session.getPosition();
        Ref<EntityStore> altarRef = session.getAltarNpcRef();

        for (int i = 0; i < toSpawn; i++) {
            Vector3d pos = WavePositions.findOpen(
                    world, altar.x, altar.z, altar.y, SPAWN_RADIUS, random);
            if (pos == null) continue;
            Ref<EntityStore> ref = spawnSeeker(store, pos);
            if (ref != null) {
                session.addSeeker(ref);
                if (altarRef != null && altarRef.isValid()) {
                    pointAtAltar(ref, store, altarRef);
                }
            }
        }
    }

    /** Removes all tracked seekers from the world. World thread. */
    private void despawnSeekers(CraftSession session, Store<EntityStore> store) {
        List<Ref<EntityStore>> seekers = session.getSeekers();
        synchronized (seekers) {
            for (Ref<EntityStore> ref : seekers) {
                if (ref != null && ref.isValid()) {
                    store.removeEntity(ref, RemoveReason.REMOVE);
                }
            }
            seekers.clear();
        }
    }

    @Nullable
    private Ref<EntityStore> spawnSeeker(Store<EntityStore> store, Vector3d position) {
        try {
            float yaw = (float) (random.nextDouble() * Math.PI * 2.0);
            var result = NPCPlugin.get().spawnNPC(
                    store, SEEKER_ROLE, null, position, new Rotation3f(0.0f, yaw, 0.0f));
            return result != null ? result.first() : null;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to spawn seeker");
            return null;
        }
    }

    /** Points the seeker's LockedTarget slot at the altar NPC so it pursues and attacks it. */
    private void pointAtAltar(Ref<EntityStore> seekerRef, Store<EntityStore> store, Ref<EntityStore> altarRef) {
        NPCEntity npc = store.getComponent(seekerRef, NPCEntity.getComponentType());
        if (npc == null) return;
        Role role = npc.getRole();
        if (role == null) return;
        try {
            role.setMarkedTarget(TARGET_SLOT, altarRef);
        } catch (Exception ignored) {}
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void endSession(World world, CraftSession session) {
        endSession(world, session, false);
    }

    private void endSession(World world, CraftSession session, boolean returnToDefault) {
        sessions.remove(world);
        lastBannerMs.remove(world);
        world.execute(() -> {
            despawnSeekers(session, world.getEntityStore().getStore());
            if (returnToDefault) {
                returnPlayersToDefaultWorld(world);
            }
        });
    }

    private void returnPlayersToDefaultWorld(World world) {
        World defaultWorld = Universe.get().getDefaultWorld();
        if (defaultWorld == null) {
            LOGGER.atWarning().log("No default world found; cannot return players from '%s'", world.getName());
            return;
        }
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            if (playerRef != null && playerRef.isValid()) {
                defaultWorld.addPlayer(playerRef);
            }
        }
    }

    private void spawnAltarNpc(CraftSession session, Store<EntityStore> store) {
        try {
            var result = NPCPlugin.get().spawnNPC(store, ALTAR_NPC_ROLE, null,
                    session.getPosition(), new Rotation3f(0.0f, 0.0f, 0.0f));
            Ref<EntityStore> ref = result != null ? result.first() : null;
            if (ref == null) {
                LOGGER.atWarning().log("Failed to spawn altar NPC role '%s'", ALTAR_NPC_ROLE);
                return;
            }
            session.setAltarNpcRef(ref);

            EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
            if (stats != null) {
                stats.setStatValue(DefaultEntityStatTypes.getHealth(), ALTAR_MAX_HEALTH);
            }
            LOGGER.atInfo().log("Spawned altar NPC '%s' with %.0f HP", ALTAR_NPC_ROLE, ALTAR_MAX_HEALTH);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Error spawning altar NPC");
        }
    }

    private boolean isAltarDestroyed(CraftSession session, Store<EntityStore> store) {
        Ref<EntityStore> ref = session.getAltarNpcRef();
        if (ref == null) return false;
        if (!ref.isValid()) return true;
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null) return false;
        EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
        return health != null && health.get() <= 0.0f;
    }

    private void broadcastTimer(World world, CraftSession session) {
        long totalSecs = session.getRemainingMs() / 1000L;
        long mins = totalSecs / 60L;
        long secs = totalSecs % 60L;
        String timeStr = String.format("%d:%02d", mins, secs);
        broadcast(world, "Craft in Progress", timeStr + " remaining — Defend the machine!", false);
    }

    private static void setTime(World world, Store<EntityStore> store, double dayTime) {
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        if (time != null) {
            time.setDayTime(dayTime, world, store);
        }
    }

    private static void broadcast(World world, String title, @Nullable String subtitle, boolean major) {
        Message titleMsg = Message.raw(title);
        Message subtitleMsg = (subtitle == null || subtitle.isBlank())
                ? Message.empty()
                : Message.raw(subtitle);
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            if (playerRef != null && playerRef.isValid()) {
                EventTitleUtil.showEventTitleToPlayer(playerRef, titleMsg, subtitleMsg, major);
            }
        }
    }
}
