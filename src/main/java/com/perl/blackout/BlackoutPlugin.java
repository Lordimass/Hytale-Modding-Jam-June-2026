package com.perl.blackout;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.riprod.patchly.PatchManager;

public final class BlackoutPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final PatchManager patchManager;

    public BlackoutPlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Blackout %s initializing...", init.getPluginManifest().getVersion());
        LOGGER.atInfo().log("Blackout Patchly %s initializing...", PatchManager.PATCHER_VERSION);
        patchManager = new PatchManager(this);
    }

    @Override
    protected void setup() {
        patchManager.install();
    }
}
