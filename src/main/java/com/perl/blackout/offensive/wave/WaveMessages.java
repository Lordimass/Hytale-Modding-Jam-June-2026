package com.perl.blackout.offensive.wave;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

import javax.annotation.Nullable;

/**
 * Broadcasts title/subtitle event messages to every player in a world.
 */
final class WaveMessages {

    private WaveMessages() {
    }

    static void broadcast(World world, String title, @Nullable String subtitle, boolean major) {
        Message titleMessage = Message.raw(title);
        Message subtitleMessage = (subtitle == null || subtitle.isBlank()) ? Message.empty() : Message.raw(subtitle);
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            if (playerRef != null && playerRef.isValid()) {
                EventTitleUtil.showEventTitleToPlayer(playerRef, titleMessage, subtitleMessage, major);
            }
        }
    }
}
