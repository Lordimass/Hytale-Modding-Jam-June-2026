package com.perl.blackout.world.commands;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

public final class BlackoutCommand extends AbstractCommand {

    public BlackoutCommand() {
        super("blackout", "Blackout world controls");
        requirePermission("blackout.command.blackout");
        setPermissionGroups("hytale:Admin");
        addSubCommand(new BlackoutToggleCommand());
        addSubCommand(new BlackoutTimerCommand());
    }

    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        context.sendMessage(Message.raw("Usage: /blackout toggle [--force on|off] [--world <world>] | /blackout timer reset [--world <world>]"));
        return null;
    }
}
