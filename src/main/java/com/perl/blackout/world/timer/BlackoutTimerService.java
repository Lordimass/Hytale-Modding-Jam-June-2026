package com.perl.blackout.world.timer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class BlackoutTimerService {
    public static final String BACKROOMS_WORLD_NAME = "Backrooms";
    public static final String OFFICE_FLOOR = "Office Floor";
    public static final String POOL_FLOOR = "Pool Floor";
    public static final String GARAGE_FLOOR = "Garage";
    public static final double POOL_FLOOR_CLEAR_Y = 4.0D;

    @Nonnull
    private final Set<UUID> pendingStarts = ConcurrentHashMap.newKeySet();

    public void requestStart(@Nonnull Ref<EntityStore> playerRef, @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        PlayerRef player = componentAccessor.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }

        World world = componentAccessor.getExternalData().getWorld();
        if (isBackroomsWorld(world)) {
            Instant now = now(componentAccessor);
            if (now == null) {
                player.sendMessage(Message.raw("Blackout timer could not start because TimeResource is unavailable."));
                return;
            }
            start(world, world.getEntityStore().getStore(), now);
            return;
        }

        this.pendingStarts.add(player.getUuid());
    }

    public void onPlayerAddedToWorld(@Nonnull AddPlayerToWorldEvent event) {
        World world = event.getWorld();
        if (!isBackroomsWorld(world)) {
            return;
        }

        PlayerRef playerRef = event.getHolder().getComponent(PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        if (!this.pendingStarts.remove(playerRef.getUuid())) {
            return;
        }

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            Instant startedAt = now(store);
            if (startedAt == null) {
                playerRef.sendMessage(Message.raw("Blackout timer could not start because TimeResource is unavailable."));
                return;
            }
            start(world, store, startedAt);
        });
    }

    public void markOfficeCleared(@Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        BlackoutTimerResource timer = timer(componentAccessor);
        if (timer == null || !timer.isRunning() || timer.getOfficeFloorClearedAt() != null) {
            return;
        }

        Instant now = now(componentAccessor);
        if (now == null) {
            return;
        }

        timer.setOfficeFloorClearedAt(now);
        World world = componentAccessor.getExternalData().getWorld();
        broadcast(world, floorClearedMessage(OFFICE_FLOOR));
    }

    public void markPoolClearedIfReached(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef, double y) {
        if (y > POOL_FLOOR_CLEAR_Y) {
            return;
        }

        BlackoutTimerResource timer = timer(store);
        if (timer == null || !timer.isRunning() || timer.getPoolFloorClearedAt() != null || timer.getOfficeFloorClearedAt() == null) {
            return;
        }

        Instant now = now(store);
        if (now == null) {
            return;
        }

        timer.setPoolFloorClearedAt(now);
        World world = store.getExternalData().getWorld();
        broadcast(world, floorClearedMessage(POOL_FLOOR));
    }

    public void stopAtGarageDoor(@Nonnull Ref<EntityStore> playerRef, @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        BlackoutTimerResource timer = timer(componentAccessor);
        if (timer == null || !timer.isRunning()) {
            return;
        }

        PlayerRef player = componentAccessor.getComponent(playerRef, PlayerRef.getComponentType());
        if (timer.getOfficeFloorClearedAt() == null || timer.getPoolFloorClearedAt() == null) {
            if (player != null) {
                player.sendMessage(Message.raw("Blackout timer cannot stop until the earlier floors are cleared."));
            }
            return;
        }

        Instant now = now(componentAccessor);
        if (now == null) {
            return;
        }

        timer.setGarageClearedAt(now);
        World world = componentAccessor.getExternalData().getWorld();
        broadcast(world, buildFinishedMessage(timer, now));
    }

    public int resetAndReturnPlayers(@Nonnull World world, @Nonnull Store<EntityStore> store) {
        BlackoutTimerResource timer = timer(store);
        if (timer != null) {
            timer.reset();
        }

        ArrayList<PlayerRef> players = new ArrayList<>(world.getPlayerRefs());
        for (PlayerRef playerRef : players) {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }

            Store<EntityStore> playerStore = ref.getStore();
            clearInventories(playerStore, ref);
            if (!tryExitInstance(ref, playerStore)) {
                teleportToSpawn(ref, playerRef, playerStore, world);
            }
        }

        try {
            InstancesPlugin.safeRemoveInstance(world);
        } catch (RuntimeException ignored) {
            // Permanent Backrooms worlds are reset in-place; real instances are closed above.
        }

        this.pendingStarts.clear();
        return players.size();
    }

    public static boolean isBackroomsWorld(@Nullable World world) {
        if (world == null) {
            return false;
        }

        String worldName = world.getName();
        return BACKROOMS_WORLD_NAME.equals(worldName) || worldName.startsWith(BACKROOMS_WORLD_NAME + "_")
                || worldName.contains(BACKROOMS_WORLD_NAME);
    }

    private void start(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull Instant startedAt) {
        BlackoutTimerResource timer = timer(store);
        if (timer == null || timer.isRunning()) {
            return;
        }

        timer.start(startedAt);
        broadcast(world, Message.join(
                Message.raw("Blackout Timer").color("#F2D16B").bold(true),
                Message.raw(" started.").color("#C9D1D9")
        ));
    }

    @Nullable
    private static BlackoutTimerResource timer(@Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        return componentAccessor.getResource(BlackoutTimerResource.getResourceType());
    }

    @Nullable
    private static Instant now(@Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        TimeResource time = componentAccessor.getResource(TimeResource.getResourceType());
        return time != null ? time.getNow() : null;
    }

    private static void clearInventories(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING).clear();
        InventoryComponent.Tool toolComponent = store.getComponent(ref, InventoryComponent.Tool.getComponentType());
        if (toolComponent != null) {
            toolComponent.getInventory().clear();
        }
    }

    private static boolean tryExitInstance(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        try {
            InstancesPlugin.exitInstance(ref, componentAccessor);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void teleportToSpawn(@Nonnull Ref<EntityStore> ref,
                                        @Nonnull PlayerRef playerRef,
                                        @Nonnull Store<EntityStore> store,
                                        @Nonnull World currentWorld) {
        World targetWorld = Universe.get().getDefaultWorld();
        if (targetWorld == null) {
            targetWorld = currentWorld;
        }

        ISpawnProvider spawnProvider = targetWorld.getWorldConfig().getSpawnProvider();
        Transform spawnTransform = spawnProvider != null ? spawnProvider.getSpawnPoint(targetWorld, playerRef.getUuid()) : new Transform();
        store.addComponent(ref, Teleport.getComponentType(), Teleport.createForPlayer(targetWorld, spawnTransform));
    }

    private static void broadcast(@Nonnull World world, @Nonnull Message chatMessage) {
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            playerRef.sendMessage(chatMessage);
        }
    }

    private static Message floorClearedMessage(@Nonnull String floorName) {
        return Message.join(
                Message.raw("Blackout Timer").color("#F2D16B").bold(true),
                Message.raw(" | ").color("#6B7280"),
                Message.raw(floorName).color("#7DD3FC").bold(true),
                Message.raw(" cleared.").color("#C9D1D9")
        );
    }

    private static Message buildFinishedMessage(@Nonnull BlackoutTimerResource timer, @Nonnull Instant finishedAt) {
        Instant startedAt = timer.getStartedAt();
        Instant officeAt = timer.getOfficeFloorClearedAt();
        Instant poolAt = timer.getPoolFloorClearedAt();

        return Message.join(
                Message.raw("Blackout Timer").color("#F2D16B").bold(true),
                Message.raw(" | Run complete").color("#C9D1D9").bold(true),
                Message.raw("\nTotal: ").color("#C9D1D9"),
                Message.raw(formatDuration(startedAt, finishedAt)).color("#A7F3D0").bold(true),
                splitLine(OFFICE_FLOOR, startedAt, officeAt, startedAt, officeAt),
                splitLine(POOL_FLOOR, officeAt, poolAt, startedAt, poolAt),
                splitLine(GARAGE_FLOOR, poolAt, finishedAt, startedAt, finishedAt)
        );
    }

    private static Message splitLine(@Nonnull String floorName,
                                     @Nullable Instant splitStart,
                                     @Nullable Instant splitEnd,
                                     @Nullable Instant totalStart,
                                     @Nullable Instant totalEnd) {
        return Message.join(
                Message.raw("\n").color("#C9D1D9"),
                Message.raw(floorName).color("#7DD3FC").bold(true),
                Message.raw(" - split: ").color("#9CA3AF"),
                Message.raw(formatDuration(splitStart, splitEnd)).color("#FFFFFF"),
                Message.raw(" | total: ").color("#9CA3AF"),
                Message.raw(formatDuration(totalStart, totalEnd)).color("#A7F3D0")
        );
    }

    private static String formatDuration(@Nullable Instant start, @Nullable Instant end) {
        if (start == null || end == null) {
            return "not recorded";
        }

        long millis = Math.max(0L, Duration.between(start, end).toMillis());
        long hours = millis / 3_600_000L;
        millis %= 3_600_000L;
        long minutes = millis / 60_000L;
        millis %= 60_000L;
        long seconds = millis / 1_000L;
        millis %= 1_000L;

        if (hours > 0L) {
            return String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, millis);
        }

        return String.format("%02d:%02d.%03d", minutes, seconds, millis);
    }
}
