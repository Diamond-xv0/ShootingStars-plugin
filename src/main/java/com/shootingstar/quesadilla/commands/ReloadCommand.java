package com.shootingstar.quesadilla.commands;

import com.shootingstar.quesadilla.ShootingStarPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import java.util.logging.Level;

public class ReloadCommand {

    private final ShootingStarPlugin plugin;

    public ReloadCommand(ShootingStarPlugin plugin) {
        this.plugin = plugin;
    }
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shootingstar.reload")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        try {
            plugin.reloadPluginConfig(); // Llama al método centralizado
            sender.sendMessage(ChatColor.GREEN + plugin.getName() + " configuration reloaded successfully!");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "An error occurred while reloading the configuration. See console for details.");
            plugin.getLogger().log(Level.SEVERE, "Error during configuration reload triggered by " + sender.getName(), e);
        }
        return true;
    }
}