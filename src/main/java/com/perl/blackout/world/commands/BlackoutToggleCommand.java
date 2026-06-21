package com.perl.blackout.world.commands;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.perl.blackout.world.resources.WorldCycleStateResource;
import com.perl.blackout.world.systems.BlackoutCycleChangedEvent;

public final class BlackoutToggleCommand extends AbstractWorldCommand {

    @Nonnull
    private final OptionalArg<ForceState> forceArg =
            this.withOptionalArg("force", "Force a specific phase instead of flipping", ArgTypes.forEnum("force", ForceState.class));

    public BlackoutToggleCommand() {
        super("toggle", "Toggle the authoritative blackout world phase");
        requirePermission("blackout.command.blackout.toggle");
        setPermissionGroups("hytale:Admin");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        WorldCycleStateResource state = store.getResource(WorldCycleStateResource.getResourceType());
        if (state == null) {
            context.sendMessage(Message.raw("Blackout world state is unavailable in this world."));
            return;
        }

        boolean on;
        if (this.forceArg.provided(context)) {
            on = this.forceArg.get(context) == ForceState.ON;
            state.setOn(on);
        } else {
            state.toggle();
            on = state.isOn();
        }

        store.invoke(new BlackoutCycleChangedEvent(world, on));

        context.sendMessage(Message.raw("Blackout phase for '" + world.getName() + "' is now " + (on ? "ON" : "OFF") + "."));
    }

    public enum ForceState {
        ON,
        OFF
    }
}
