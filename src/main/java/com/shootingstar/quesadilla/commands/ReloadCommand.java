package com.shootingstar.quesadilla.commands;

import com.shootingstar.quesadilla.ShootingStarPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

public class ReloadCommand implements CommandExecutor {

    private final ShootingStarPlugin plugin;

    public ReloadCommand(ShootingStarPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("shootingstars.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission");
            return true;
        }
        
        plugin.clearLocalData();
        sender.sendMessage(ChatColor.YELLOW + "Local data cleared, reloading config...");

        plugin.reloadConfig();

        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.loadLocalData();
                sender.sendMessage(ChatColor.GREEN + "Config reloaded and local data updated!");
            }
        }.runTaskLater(plugin, 20L);

        return true;
    }
}
