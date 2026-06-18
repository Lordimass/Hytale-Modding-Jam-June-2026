package com.perl.blackout.offensive;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.RemovedPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.perl.blackout.offensive.wave.WaveGameManager;
import com.perl.blackout.offensive.wave.WaveSystem;

import javax.annotation.Nullable;

/**
 * The offensive / enemies sub-plugin. Hosts the wave system (main game loop): players entering the
 * Backrooms instance defend a stationary crafting machine against waves of enemies that spawn at
 * night and are despawned during the day.
 */
public class OffensivePlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nullable
    private static OffensivePlugin instance;

    @Nullable
    private WaveGameManager waveGameManager;

    public OffensivePlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Blackout Offensive %s initializing...", init.getPluginManifest().getVersion());
    }

    @Nullable
    public static OffensivePlugin getInstance() {
        return instance;
    }

    @Nullable
    public WaveGameManager getWaveGameManager() {
        return waveGameManager;
    }

    @Override
    protected void setup() {
        instance = this;
        this.waveGameManager = new WaveGameManager(this);

        getEntityStoreRegistry().registerSystem(new WaveSystem());

        getEventRegistry().registerGlobal(RemovedPlayerFromWorldEvent.class, this::onPlayerRemovedFromWorld);
        getEventRegistry().registerGlobal(RemoveWorldEvent.class, event -> {
            if (waveGameManager != null) {
                waveGameManager.onWorldRemoved(event.getWorld());
            }
        });

        LOGGER.atInfo().log("Blackout Offensive wave system ready.");
    }

    @Override
    protected void shutdown() {
        if (waveGameManager != null) {
            waveGameManager.shutdown();
        }
        instance = null;
        super.shutdown();
    }

    private void onPlayerRemovedFromWorld(RemovedPlayerFromWorldEvent event) {
        if (waveGameManager == null) {
            return;
        }
        var holder = event.getHolder();
        PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
        Player player = holder.getComponent(Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }
        waveGameManager.onPlayerRemovedFromWorld(playerRef, player, event.getWorld());
    }
}
