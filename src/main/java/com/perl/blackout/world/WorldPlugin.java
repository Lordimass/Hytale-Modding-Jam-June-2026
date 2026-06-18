package com.perl.blackout.world;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.perl.blackout.world.craft.CraftAltarBreakHandler;
import com.perl.blackout.world.craft.CraftAltarPlacementHandler;

/**
 * World sub-plugin. Registers the crafting-machine place/break handlers, which forward to the
 * Offensive wave game (the machine becomes the optional defended bench inside an instance).
 */
public class WorldPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public WorldPlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Blackout World %s initializing...", init.getPluginManifest().getVersion());
    }

    @Override
    protected void setup() {
        getEntityStoreRegistry().registerSystem(new CraftAltarPlacementHandler());
        getEntityStoreRegistry().registerSystem(new CraftAltarBreakHandler());

        LOGGER.atInfo().log("Blackout World crafting-machine handlers ready.");
    }
}
