package com.perl.blackout.world.commands;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.perl.blackout.world.WorldPlugin;
import com.perl.blackout.world.timer.BlackoutTimerService;

public final class BlackoutTimerResetCommand extends AbstractWorldCommand {

    public BlackoutTimerResetCommand() {
        super("reset", "Reset the Blackout timer run and return players to spawn");
        requirePermission("blackout.command.blackout.timer.reset");
        setPermissionGroups("hytale:Admin");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        BlackoutTimerService service = WorldPlugin.getTimerService();
        if (service == null) {
            context.sendMessage(Message.raw("Blackout timer service is unavailable."));
            return;
        }

        int playerCount = service.resetAndReturnPlayers(world, store);
        context.sendMessage(Message.raw("Blackout timer reset for '" + world.getName() + "'. Returned " + playerCount
                + " player(s), cleared inventories, and queued the instance to close if applicable."));
    }
}
