package com.shootingstar.quesadilla.commands;

import com.shootingstar.quesadilla.ShootingStarPlugin;
import com.shootingstar.quesadilla.StarConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.Optional;
public class SpawnCommand {

    private final ShootingStarPlugin plugin;

    public SpawnCommand(ShootingStarPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shootingstar.command.spawn")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to spawn stars manually.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /sh spawn <star_name> [<player> | <x> <y> <z> [<world>]]");
            return true;
        }

        String starConfigKey = args[0];

        StarConfig starConfig = plugin.getConfigManager().getStarConfig(starConfigKey);
        if (starConfig == null) {
            sender.sendMessage(ChatColor.RED + "Configuration for star '" + starConfigKey + "' not found!");
            return true;
        }

        Location baseLocation = parseLocationArgs(sender, args);
        if (baseLocation == null) return true;

        int altitudeOffset = plugin.getRandom().nextInt(starConfig.getMaxAltitude() - starConfig.getMinAltitude() + 1) + starConfig.getMinAltitude();

        Location spawnLoc = baseLocation.clone().add(0, altitudeOffset, 0);

        if (spawnLoc.getY() >= spawnLoc.getWorld().getMaxHeight()) {
            spawnLoc.setY(spawnLoc.getWorld().getMaxHeight() - 1);
        }
        if (spawnLoc.getY() < spawnLoc.getWorld().getMinHeight()) {
            spawnLoc.setY(spawnLoc.getWorld().getMinHeight());
        }


        Optional<org.bukkit.entity.Item> spawnedItemOpt = plugin.spawnShootingStar(starConfig, spawnLoc);

        if (spawnedItemOpt.isPresent()) {
            sender.sendMessage(ChatColor.GREEN + "Spawned star '" + ChatColor.WHITE + starConfigKey + ChatColor.GREEN + "' near " + formatLocation(baseLocation) + " (spawned at Y=" + String.format("%.1f", spawnLoc.getY()) + ")");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to spawn star '" + starConfigKey + "'. Check console for errors.");
        }

        return true;
    }
    private Location parseLocationArgs(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (sender instanceof Player) {
                return ((Player) sender).getLocation();
            } else {
                sender.sendMessage(ChatColor.RED + "Console must specify a target player or coordinates.");
                return null;
            }
        }
        else if (args.length == 2 && !isNumeric(args[1])) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null) {
                return target.getLocation();
            } else {
                sender.sendMessage(ChatColor.RED + "Player '" + args[1] + "' not found.");
                return null;
            }
        }
        else if (args.length >= 4 && isNumeric(args[1]) && isNumeric(args[2]) && isNumeric(args[3])) {
            try {
                double x = Double.parseDouble(args[1]);
                double y = Double.parseDouble(args[2]);
                double z = Double.parseDouble(args[3]);
                World world = null;

                if (args.length >= 5) {
                    world = Bukkit.getWorld(args[4]);
                    if (world == null) {
                        sender.sendMessage(ChatColor.RED + "World '" + args[4] + "' not found.");
                        return null;
                    }
                } else {
                    if (sender instanceof Player) {
                        world = ((Player) sender).getWorld();
                    } else {
                        sender.sendMessage(ChatColor.RED + "Console must specify a world when using coordinates.");
                        return null;
                    }
                }
                return new Location(world, x, y, z);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid coordinate format.");
                return null;
            }
        }
        else {
            sender.sendMessage(ChatColor.RED + "Invalid arguments. Usage: /sh spawn <name> [<player> | <x> <y> <z> [<world>]]");
            return null;
        }
    }

    private boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String formatLocation(Location loc) {
        if (loc == null) return "N/A";
        return String.format("%s[%.1f, %.1f, %.1f]",
                loc.getWorld() != null ? loc.getWorld().getName() : "UnknownWorld",
                loc.getX(), loc.getY(), loc.getZ());
    }
}
