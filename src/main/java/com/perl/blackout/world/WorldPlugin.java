package com.perl.blackout.world;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.perl.blackout.world.craft.CraftAltarBreakHandler;
import com.perl.blackout.world.craft.CraftAltarManager;
import com.perl.blackout.world.craft.CraftAltarPlacementHandler;
import com.perl.blackout.world.craft.CraftAltarSystem;

public class WorldPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public WorldPlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Blackout World %s initializing...", init.getPluginManifest().getVersion());
    }

    @Override
    protected void setup() {
        CraftAltarManager.getInstance();

        getEntityStoreRegistry().registerSystem(new CraftAltarSystem());
        getEntityStoreRegistry().registerSystem(new CraftAltarPlacementHandler());
        getEntityStoreRegistry().registerSystem(new CraftAltarBreakHandler());

        getEventRegistry().registerGlobal(RemoveWorldEvent.class,
                event -> CraftAltarManager.getInstance().onWorldRemoved(event.getWorld()));

        LOGGER.atInfo().log("Craft altar system ready.");
    }

    @Override
    protected void shutdown() {
        CraftAltarManager.getInstance().shutdown();
        super.shutdown();
    }
}
