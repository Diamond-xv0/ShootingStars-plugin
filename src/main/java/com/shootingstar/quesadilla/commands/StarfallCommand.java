package com.shootingstar.quesadilla.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class StarfallCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final Random random = new Random();
    private BukkitRunnable currentStarfallTask;

    public StarfallCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check permission
        if (!sender.hasPermission("shootingstars.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to execute this command.");
            return true;
        }

        if (!(args.length == 2 || args.length == 3 || args.length == 5 || args.length == 6)) {
            sender.sendMessage(ChatColor.RED + "Usage: /starfall <starname1,starname2,...> <amount> [<player> | <x> <y> <z> [<world>]]");
            return true;
        }

        String[] starNames = args[0].split(",");
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid amount.");
            return true;
        }

        Location baseLocation = parseLocationArgs(sender, args);
        if (baseLocation == null) return true;

        if (currentStarfallTask != null) {
            currentStarfallTask.cancel();
        }

        currentStarfallTask = new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= amount) {
                    cancel();
                    return;
                }

                for (String starName : starNames) {
                    ConfigurationSection starConfig = plugin.getConfig().getConfigurationSection(starName);
                    if (starConfig == null) {
                        sender.sendMessage(ChatColor.RED + "Config not found for star: " + starName);
                        continue;
                    }

                    double offsetX = random.nextInt(101) - 50; // Range: -50 to 50
                    double offsetZ = random.nextInt(101) - 50; // Range: -50 to 50
                    int offsetY = calculateAltitudeOffset(starConfig); // Y offset based on config
                    Location spawnLoc = baseLocation.clone().add(offsetX, offsetY, offsetZ);

                    ItemStack itemStack = createItemFromConfig(starConfig);
                    if (itemStack == null) continue;

                    Item droppedItem = spawnItem(spawnLoc, itemStack);

                    spawnEffects(starConfig, droppedItem);
                }

                count++;
            }
        };

        // Schedule the task with a 7-tick interval
        currentStarfallTask.runTaskTimer(plugin, 0L, 7L);
        sender.sendMessage(ChatColor.GREEN + "Starfall started! Generating " + amount + " stars.");
        return true;
    }

    public void stopStarfall() {
        if (currentStarfallTask != null) {
            currentStarfallTask.cancel();
            currentStarfallTask = null;
        }
    }

    private Location parseLocationArgs(CommandSender sender, String[] args) {
        if (args.length == 2) {
            if (sender instanceof Player) {
                return ((Player) sender).getLocation();
            } else {
                sender.sendMessage(ChatColor.RED + "You must specify a location when running from the console.");
                return null;
            }
        } else if (args.length == 3) {
            Player target = Bukkit.getPlayer(args[2]);
            if (target != null) {
                return target.getLocation();
            } else {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[2]);
                return null;
            }
        } else if (args.length == 5 || args.length == 6) {
            try {
                double x = Double.parseDouble(args[2]);
                double y = Double.parseDouble(args[3]);
                double z = Double.parseDouble(args[4]);
                World world;
                if (args.length == 6) {
                    world = Bukkit.getWorld(args[5]);
                    if (world == null) {
                        sender.sendMessage(ChatColor.RED + "World not found: " + args[5]);
                        return null;
                    }
                } else if (sender instanceof Player) {
                    world = ((Player) sender).getWorld();
                } else {
                    sender.sendMessage(ChatColor.RED + "You must specify a world when running from the console.");
                    return null;
                }
                return new Location(world, x, y, z);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid coordinates.");
                return null;
            }
        }
        sender.sendMessage(ChatColor.RED + "Invalid usage of arguments.");
        return null;
    }

    private ItemStack createItemFromConfig(ConfigurationSection starConfig) {
        String itemStr = starConfig.getString("item", "NETHER_STAR");
        Material material = Material.matchMaterial(itemStr.toUpperCase());
        if (material == null) {
            plugin.getLogger().warning("Invalid material: " + itemStr);
            return null;
        }

        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', starConfig.getString("name", "Falling Star")));
            List<String> loreConfig = starConfig.getStringList("lore");
            List<String> lore = new ArrayList<>();
            for (String line : loreConfig) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private int calculateAltitudeOffset(ConfigurationSection starConfig) {
        String altitudeRange = starConfig.getString("altitude", "25-50");
        String[] parts = altitudeRange.split("-");
        int minAlt = Integer.parseInt(parts[0].trim());
        int maxAlt = Integer.parseInt(parts[1].trim());
        return random.nextInt(maxAlt - minAlt + 1) + minAlt;
    }

    private Item spawnItem(Location spawnLoc, ItemStack itemStack) {
        Item droppedItem = spawnLoc.getWorld().dropItem(spawnLoc, itemStack);
        droppedItem.setVelocity(new Vector(0, -0.5, 0));
        return droppedItem;
    }

    private void spawnEffects(ConfigurationSection starConfig, Item droppedItem) {
        Particle particle = getParticle(starConfig);
        String soundStr = starConfig.getString("sound", "ENTITY_FIREWORK_ROCKET_LAUNCH").toUpperCase();
        Sound sound = getSound(soundStr);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!droppedItem.isValid() || droppedItem.isOnGround()) {
                    cancel();
                    return;
                }
                Location current = droppedItem.getLocation();
                for (int i = 0; i < 5; i++) {
                    Location particleLoc = current.clone().subtract(0, i * 0.5, 0);
                    current.getWorld().spawnParticle(particle, particleLoc, 5, 0.2, 0.2, 0.2, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!droppedItem.isValid() || droppedItem.isOnGround()) {
                    cancel();
                    return;
                }
                droppedItem.getWorld().playSound(droppedItem.getLocation(), sound, 1.0f, 1.0f);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private Particle getParticle(ConfigurationSection starConfig) {
        String particleStr = starConfig.getString("particles", "FLAME").toUpperCase();
        try {
            return Particle.valueOf(particleStr);
        } catch (Exception e) {
            return Particle.FLAME;
        }
    }

    private Sound getSound(String soundStr) {
        try {
            return Sound.valueOf(soundStr);
        } catch (Exception e) {
            return Sound.ENTITY_FIREWORK_ROCKET_LAUNCH;
        }
    }
}
