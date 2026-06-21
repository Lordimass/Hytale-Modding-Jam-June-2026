package com.perl.blackout.world.commands;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

public final class BlackoutTimerCommand extends AbstractCommand {

    public BlackoutTimerCommand() {
        super("timer", "Blackout timer controls");
        requirePermission("blackout.command.blackout.timer");
        setPermissionGroups("hytale:Admin");
        addSubCommand(new BlackoutTimerResetCommand());
    }

    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        context.sendMessage(Message.raw("Usage: /blackout timer reset [--world <world>]"));
        return CompletableFuture.completedFuture(null);
    }
}
