package com.shootingstar.quesadilla;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReloadCommand implements CommandExecutor {

    private final ShootingStarPlugin plugin;

    public ReloadCommand(ShootingStarPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Verifica si el sender es un jugador y si tiene permiso
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!player.hasPermission("shootingstars.admin")) {
                player.sendMessage(ChatColor.RED + "No tienes permiso para usar este comando.");
                return true;
            }
        }

        // Recargar la configuración
        plugin.reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "¡Configuración recargada correctamente!");

        return true;
    }
}

