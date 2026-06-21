package com.perl.blackout.world;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.perl.blackout.world.commands.BlackoutCommand;
import com.perl.blackout.world.components.BunkerHatchComponent;
import com.perl.blackout.world.components.CycleStateComponent;
import com.perl.blackout.world.craft.CraftAltarBreakHandler;
import com.perl.blackout.world.craft.CraftAltarBreathingHandler;
import com.perl.blackout.world.craft.CraftAltarPlacementHandler;
import com.perl.blackout.world.interactions.BlackoutPhaseGateInteraction;
import com.perl.blackout.world.resources.WorldCycleStateResource;
import com.perl.blackout.world.systems.CyclePhaseApplySystem;
import com.perl.blackout.world.systems.CycleStateRefSystem;
import com.perl.blackout.world.systems.CycleTimeSetSystem;

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
        getEntityStoreRegistry().registerSystem(new CraftAltarBreathingHandler());

        LOGGER.atInfo().log("Craft altar system ready.");

        /**
         * ChunkStore Stuff
         */
        var chunkStoreRegistry = getChunkStoreRegistry();
        ComponentType<ChunkStore, CycleStateComponent> cycleComponentType = chunkStoreRegistry
                .registerComponent(CycleStateComponent.class, "BlackoutCycleState", CycleStateComponent.CODEC);
        CycleStateComponent.setComponentType(cycleComponentType);

        ComponentType<ChunkStore, BunkerHatchComponent> bunkerHatchComponentType = chunkStoreRegistry
                .registerComponent(BunkerHatchComponent.class, "BlackoutBunkerHatch", BunkerHatchComponent.CODEC);
        BunkerHatchComponent.setComponentType(bunkerHatchComponentType);
        
        chunkStoreRegistry.registerSystem(new CycleStateRefSystem());

        /**
         * EntityStore Stuff
         */
        ResourceType<EntityStore, WorldCycleStateResource> cycleResourceType = getEntityStoreRegistry()
                .registerResource(WorldCycleStateResource.class, "BlackoutWorldCycleState",
                        WorldCycleStateResource.CODEC);
        WorldCycleStateResource.setResourceType(cycleResourceType);

        getEntityStoreRegistry().registerSystem(new CyclePhaseApplySystem());
        getEntityStoreRegistry().registerSystem(new CycleTimeSetSystem());

        /**
         * Command Stuff
         */
        getCommandRegistry().registerCommand(new BlackoutCommand());

        /**
         * Interaction Stuff
         */
        Interaction.CODEC.register("BlackoutPhaseGate", BlackoutPhaseGateInteraction.class,
                BlackoutPhaseGateInteraction.CODEC);

        LOGGER.atInfo().log("World cycle state system ready.");
    }

    @Override
    protected void shutdown() {
        super.shutdown();
    }
}
