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

public class SpawnCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    public SpawnCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shootingstars.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Use: /sh spawn <name> [<player> | <x> <y> <z> [<world>]]");
            return true;
        }

        String starConfigKey = args[0];
        Location baseLocation = null;
        World world = null;

        if (args.length == 1 && sender instanceof Player) {
            baseLocation = ((Player) sender).getLocation();
            world = baseLocation.getWorld();
        } else if (args.length == 2 && !(args[1].equalsIgnoreCase("help") || isNumeric(args[1]))) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target != null) {
                baseLocation = target.getLocation();
                world = target.getWorld();
            } else {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }
        } else if (args.length >= 4) {
            baseLocation = parseLocationArgs(sender, args);
            if (baseLocation == null) return true;
            world = baseLocation.getWorld();
        } else {
            sender.sendMessage(ChatColor.RED + "Incorrect usage.");
            return true;
        }

        ConfigurationSection starConfig = plugin.getConfig().getConfigurationSection(starConfigKey);
        if (starConfig == null) {
            sender.sendMessage(ChatColor.RED + "Config not found for star: " + starConfigKey);
            return true;
        }

        ItemStack itemStack = createItemFromConfig(starConfig);
        if (itemStack == null) return true;

        int offsetY = calculateAltitudeOffset(starConfig);
        Location spawnLoc = baseLocation.clone().add(0, offsetY, 0);

        Item droppedItem = spawnItem(spawnLoc, itemStack);

        spawnEffects(starConfig, droppedItem);

        sender.sendMessage(ChatColor.GREEN + "The star '" + starConfigKey + "' was spawned in location, elevated " + offsetY + " blocks.");
        return true;
    }

    private Location parseLocationArgs(CommandSender sender, String[] args) {
        try {
            double x = Double.parseDouble(args[1]);
            double y = Double.parseDouble(args[2]);
            double z = Double.parseDouble(args[3]);
            World world = (args.length == 5) ? Bukkit.getWorld(args[4]) : ((Player) sender).getWorld();
            if (world == null) {
                sender.sendMessage(ChatColor.RED + "World not found.");
                return null;
            }
            return new Location(world, x, y, z);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            sender.sendMessage(ChatColor.RED + "Invalid coordinates or world.");
            return null;
        }
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
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', starConfig.getString("name", "Estrella Fugaz")));
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

    private boolean isNumeric(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch(NumberFormatException e) {
            return false;
        }
    }
}
