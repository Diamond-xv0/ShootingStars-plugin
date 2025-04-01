package com.shootingstar.quesadilla.commands;

import com.shootingstar.quesadilla.ShootingStarPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ReloadCommand implements CommandExecutor {

    private final ShootingStarPlugin plugin;

    public ReloadCommand(ShootingStarPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Verifica si el sender es un jugador y si tiene permiso
        if (!sender.hasPermission("shootingstars.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission");
            return true;
        }

        // Recargar la configuración
        plugin.reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "¡Config reloaded!");

        return true;
    }
}
