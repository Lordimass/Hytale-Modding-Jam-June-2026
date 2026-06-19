package com.perl.blackout.world.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.perl.blackout.world.resources.WorldCycleStateResource;

public final class BlackoutPhaseGateInteraction extends SimpleInteraction {

    @Nonnull
    public static final BuilderCodec<BlackoutPhaseGateInteraction> CODEC = BuilderCodec.builder(
            BlackoutPhaseGateInteraction.class, BlackoutPhaseGateInteraction::new, SimpleInteraction.CODEC)
            .documentation("Proceeds to Next only while the Blackout world phase is on.")
            .build();

    @Override
    protected void tick0(boolean firstRun, float time, @Nonnull InteractionType type,
                         @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        boolean shouldBurn = false;
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer != null) {
            WorldCycleStateResource phase = commandBuffer.getResource(WorldCycleStateResource.getResourceType());
            shouldBurn = phase != null && phase.isOn();
        }

        context.getState().state = shouldBurn ? InteractionState.Finished : InteractionState.Failed;
        super.tick0(firstRun, time, type, context, cooldownHandler);
    }
}
