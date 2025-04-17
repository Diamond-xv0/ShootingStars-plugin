package com.shootingstar.quesadilla.commands;

import com.shootingstar.quesadilla.ShootingStarPlugin;
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
            sendHelp(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subCommand) {
            case "reload":
                return new ReloadCommand(plugin).execute(sender, subArgs);
            case "spawn":
                return new SpawnCommand(plugin).execute(sender, subArgs);
            case "starfall":
                return new StarfallCommand(plugin).execute(sender, subArgs);
            case "help":
            case "?":
                sendHelp(sender, label);
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown sub-command: " + subCommand);
                sendHelp(sender, label);
                return true;
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "--- ShootingStar Commands ---");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload" + ChatColor.GRAY + " - Reloads the configuration.");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " spawn <name> [<player>|<x> <y> <z> [world]]" + ChatColor.GRAY + " - Spawns a specific star.");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " starfall <name,...> <amount> [<player>|<x> <y> <z> [world]]" + ChatColor.GRAY + " - Starts a starfall.");
    }
}
