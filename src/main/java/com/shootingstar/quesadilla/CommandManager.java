package com.shootingstar.quesadilla;

import com.shootingstar.quesadilla.commands.ReloadCommand;
import com.shootingstar.quesadilla.commands.SpawnCommand;
import com.shootingstar.quesadilla.commands.StarfallCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class CommandManager implements CommandExecutor {

    private final ShootingStarPlugin plugin;

    public CommandManager(ShootingStarPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Use: /sh <action>");
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subCommand) {
            case "reload":
                return new ReloadCommand(plugin).onCommand(sender, command, label, subArgs);
            case "spawn":
                return new SpawnCommand(plugin).onCommand(sender, command, label, subArgs);
            case "starfall":
                return new StarfallCommand(plugin).onCommand(sender, command, label, subArgs);
            default:
                sender.sendMessage(ChatColor.RED + "Unknown action");
                return true;
        }
    }
}

