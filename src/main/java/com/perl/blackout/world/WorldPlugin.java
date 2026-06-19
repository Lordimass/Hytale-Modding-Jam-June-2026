package com.perl.blackout.world;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.perl.blackout.world.commands.BlackoutCommand;
import com.perl.blackout.world.components.CycelStateComponent;
import com.perl.blackout.world.craft.CraftAltarBreakHandler;
import com.perl.blackout.world.craft.CraftAltarPlacementHandler;
import com.perl.blackout.world.resources.WorldCycleStateResource;
import com.perl.blackout.world.systems.CycleStateRefSystem;
import com.perl.blackout.world.systems.WorldCycleDriverSystem;

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

        LOGGER.atInfo().log("Craft altar system ready.");

        ComponentType<ChunkStore, CycelStateComponent> cycleComponentType =
                getChunkStoreRegistry().registerComponent(CycelStateComponent.class, "BlackoutCycleState", CycelStateComponent.CODEC);
        CycelStateComponent.setComponentType(cycleComponentType);

        ResourceType<EntityStore, WorldCycleStateResource> cycleResourceType =
                getEntityStoreRegistry().registerResource(WorldCycleStateResource.class, "BlackoutWorldCycleState", WorldCycleStateResource.CODEC);
        WorldCycleStateResource.setResourceType(cycleResourceType);

        getEntityStoreRegistry().registerSystem(new WorldCycleDriverSystem());
        getChunkStoreRegistry().registerSystem(new CycleStateRefSystem());

        getCommandRegistry().registerCommand(new BlackoutCommand());

        LOGGER.atInfo().log("World cycle state system ready.");
    }

    @Override
    protected void shutdown() {
        super.shutdown();
    }
}
