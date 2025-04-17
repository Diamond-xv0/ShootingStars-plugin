package com.shootingstar.quesadilla.commands;

import com.shootingstar.quesadilla.ShootingStarPlugin;
import com.shootingstar.quesadilla.StarConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class StarfallCommand {

    private final ShootingStarPlugin plugin;
    private final Random random = new Random();
    private static BukkitTask currentStarfallTask = null;

    public StarfallCommand(ShootingStarPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shootingstar.command.starfall")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to start a starfall.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("stop")) {
            return stopStarfall(sender);
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /sh starfall <name1,name2,...> <amount> [<player> | <x> <y> <z> [<world>]]");
            sender.sendMessage(ChatColor.RED + "Usage: /sh starfall stop");
            return true;
        }

        String[] starNamesInput = args[0].split(",");
        List<StarConfig> validStarConfigs = new ArrayList<>();
        for (String name : starNamesInput) {
            StarConfig cfg = plugin.getConfigManager().getStarConfig(name.trim());
            if (cfg != null) {
                validStarConfigs.add(cfg);
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Warning: Star configuration '" + name.trim() + "' not found, skipping.");
            }
        }

        if (validStarConfigs.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No valid star configurations found for the names provided.");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid amount: Must be a positive whole number.");
            return true;
        }

        Location baseLocation = parseLocationArgs(sender, args, 2);
        if (baseLocation == null) return true;

        if (currentStarfallTask != null && !currentStarfallTask.isCancelled()) {
            currentStarfallTask.cancel();
            sender.sendMessage(ChatColor.YELLOW + "Previous starfall task stopped.");
        }

        long starfallInterval = plugin.getConfig().getLong("settings.starfall.interval-ticks", 7L);
        int starfallRadius = plugin.getConfig().getInt("settings.starfall.radius", 50);

        currentStarfallTask = new BukkitRunnable() {
            int spawnedCount = 0;

            @Override
            public void run() {
                if (spawnedCount >= amount) {
                    sender.sendMessage(ChatColor.GREEN + "Starfall finished (" + spawnedCount + " stars spawned).");
                    cancel(); // Finaliza la tarea
                    currentStarfallTask = null; // Limpiar referencia
                    return;
                }

                StarConfig randomStarConfig = validStarConfigs.get(random.nextInt(validStarConfigs.size()));

                double offsetX = random.nextDouble() * starfallRadius * 2 - starfallRadius;
                double offsetZ = random.nextDouble() * starfallRadius * 2 - starfallRadius;

                int altitudeOffset = plugin.getRandom().nextInt(randomStarConfig.getMaxAltitude() - randomStarConfig.getMinAltitude() + 1) + randomStarConfig.getMinAltitude();
                Location spawnLoc = baseLocation.clone().add(offsetX, altitudeOffset, offsetZ);

                if (spawnLoc.getY() >= spawnLoc.getWorld().getMaxHeight()) spawnLoc.setY(spawnLoc.getWorld().getMaxHeight() - 1);
                if (spawnLoc.getY() < spawnLoc.getWorld().getMinHeight()) spawnLoc.setY(spawnLoc.getWorld().getMinHeight());

                plugin.spawnShootingStar(randomStarConfig, spawnLoc);

                spawnedCount++;

                if (spawnedCount % 10 == 0 && spawnedCount < amount) {
                    sender.sendMessage(ChatColor.GRAY + "Starfall progress: " + spawnedCount + "/" + amount);
                }
            }
        }.runTaskTimer(plugin, 0L, starfallInterval);

        sender.sendMessage(ChatColor.GREEN + "Starfall started! Spawning " + amount + " stars ("+ String.join(", ", starNamesInput) +") around " + formatLocation(baseLocation) + ".");
        sender.sendMessage(ChatColor.YELLOW + "Use '/sh starfall stop' to cancel.");
        return true;
    }

    private boolean stopStarfall(CommandSender sender) {
        if (currentStarfallTask != null && !currentStarfallTask.isCancelled()) {
            currentStarfallTask.cancel();
            currentStarfallTask = null;
            sender.sendMessage(ChatColor.GREEN + "Starfall task stopped.");
            return true;
        } else {
            sender.sendMessage(ChatColor.YELLOW + "No active starfall task to stop.");
            return true;
        }
    }


    private Location parseLocationArgs(CommandSender sender, String[] args, int startIndex) {
        if (args.length == startIndex) {
            if (sender instanceof Player) {
                return ((Player) sender).getLocation();
            } else {
                sender.sendMessage(ChatColor.RED + "Console must specify a target player or coordinates.");
                return null;
            }
        }
        else if (args.length == startIndex + 1 && !isNumeric(args[startIndex])) {
            Player target = Bukkit.getPlayerExact(args[startIndex]);
            if (target != null) {
                return target.getLocation();
            } else {
                sender.sendMessage(ChatColor.RED + "Player '" + args[startIndex] + "' not found.");
                return null;
            }
        }
        else if (args.length >= startIndex + 3 && isNumeric(args[startIndex]) && isNumeric(args[startIndex+1]) && isNumeric(args[startIndex+2])) {
            try {
                double x = Double.parseDouble(args[startIndex]);
                double y = Double.parseDouble(args[startIndex+1]);
                double z = Double.parseDouble(args[startIndex+2]);
                World world = null;

                if (args.length >= startIndex + 4) {
                    world = Bukkit.getWorld(args[startIndex+3]);
                    if (world == null) {
                        sender.sendMessage(ChatColor.RED + "World '" + args[startIndex+3] + "' not found.");
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
            sender.sendMessage(ChatColor.RED + "Invalid location arguments.");
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
