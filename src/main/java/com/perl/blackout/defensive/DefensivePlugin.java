package com.perl.blackout.defensive;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.perl.blackout.defensive.interactions.ConsumeItemInteraction;
import com.perl.blackout.defensive.motion.builders.BuilderHeadMotionTurretAim;

public class DefensivePlugin  extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public DefensivePlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Blackout Defensive %s initializing...", init.getPluginManifest().getVersion());
    }

    @Override
    protected void setup() {
        Interaction.CODEC.register("ConsumeItem", ConsumeItemInteraction.class, ConsumeItemInteraction.CODEC);
        NPCPlugin.get().registerCoreComponentType("TurretAim", BuilderHeadMotionTurretAim::new);
    }
}
