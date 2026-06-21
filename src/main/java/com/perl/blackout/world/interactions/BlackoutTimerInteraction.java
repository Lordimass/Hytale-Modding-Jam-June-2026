package com.perl.blackout.world.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.perl.blackout.world.WorldPlugin;
import com.perl.blackout.world.timer.BlackoutTimerService;

public final class BlackoutTimerInteraction extends SimpleInstantInteraction {

    @Nonnull
    public static final BuilderCodec<BlackoutTimerInteraction> CODEC =
            BuilderCodec.builder(BlackoutTimerInteraction.class, BlackoutTimerInteraction::new,
                            SimpleInstantInteraction.CODEC)
                    .append(new KeyedCodec<>("Action", new EnumCodec<>(Action.class)),
                            (interaction, action) -> interaction.action = action, interaction -> interaction.action)
                    .documentation("Timer action to run on the server.")
                    .add()
                    .build();

    @Nonnull
    private Action action = Action.START;

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        BlackoutTimerService service = WorldPlugin.getTimerService();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Ref<EntityStore> playerRef = context.getEntity();
        if (service == null || commandBuffer == null || playerRef == null || !playerRef.isValid()) {
            return;
        }

        switch (this.action) {
            case START -> service.requestStart(playerRef, commandBuffer);
            case CLEAR_OFFICE -> service.markOfficeCleared(commandBuffer);
            case STOP -> service.stopAtGarageDoor(playerRef, commandBuffer);
        }
    }

    public enum Action {
        START,
        CLEAR_OFFICE,
        STOP
    }
}
