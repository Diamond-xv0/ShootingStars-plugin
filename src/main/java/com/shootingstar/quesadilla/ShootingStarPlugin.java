package com.shootingstar.quesadilla;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class ShootingStarPlugin extends JavaPlugin {

    private final Random random = new Random();
    private Map<String, Object> localData = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Objects.requireNonNull(this.getCommand("sh")).setExecutor(new CommandManager(this));
        loadLocalData();

        new BukkitRunnable() {
            @Override
            public void run() {
                ConfigurationSection rootSection = getConfig().getConfigurationSection("");
                if (rootSection == null) return;

                for (String key : rootSection.getKeys(false)) {
                    String worldName = getConfig().getString(key + ".world", "world");
                    World configuredWorld = Bukkit.getWorld(worldName);
                    if (configuredWorld == null) {
                        getLogger().warning("World not found: " + worldName + " for key: " + key);
                        continue;
                    }

                    String timeOption = getConfig().getString(key + ".time", "night").toLowerCase();
                    double probability = getConfig().getDouble(key + ".probability", 0);
                    boolean skyRequired = getConfig().getBoolean(key + ".sky", false);
                    String altitudeRange = getConfig().getString(key + ".altitude", "25-50");
                    String itemStr = getConfig().getString(key + ".item", "NETHER_STAR");
                    String displayName = ChatColor.translateAlternateColorCodes('&',
                            getConfig().getString(key + ".name", "Falling Star"));

                    String[] parts = altitudeRange.split("-");
                    int minAlt, maxAlt;
                    try {
                        minAlt = Integer.parseInt(parts[0].trim());
                        maxAlt = Integer.parseInt(parts[1].trim());
                    } catch (Exception e) {
                        getLogger().warning("Invalid altitude range for key: " + key);
                        continue;
                    }

                    for (Player player : configuredWorld.getPlayers()) {
                        long currentTime = player.getWorld().getTime();
                        if (("night".equals(timeOption) && (currentTime < 13000 || currentTime > 23000))
                                || ("day".equals(timeOption) && (currentTime >= 13000 && currentTime <= 23000))) {
                            continue;
                        }

                        if (random.nextDouble() * 10 > probability) continue;

                        Location randomLoc = getRandomLocationAround(player.getLocation(), 15);
                        if (skyRequired && randomLoc.getBlock().getRelative(BlockFace.UP).getType() != Material.AIR) {
                            continue;
                        }

                        int offsetY = random.nextInt(maxAlt - minAlt + 1) + minAlt;
                        Location spawnLoc = randomLoc.clone().add(0, offsetY, 0);

                        Material material = Material.matchMaterial(itemStr.toUpperCase());
                        if (material == null) {
                            getLogger().warning("Invalid material for key: " + key + " -> " + itemStr);
                            continue;
                        }
                        ItemStack itemStack = new ItemStack(material);
                        ItemMeta meta = itemStack.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(displayName);
                            meta.setLore(ChatColor.translateAlternateColorCodes('&',
                                    String.join("\n", getConfig().getStringList(key + ".lore"))).lines().toList());
                            itemStack.setItemMeta(meta);
                        }

                        Item droppedItem = spawnLoc.getWorld().dropItem(spawnLoc, itemStack);
                        droppedItem.setVelocity(new Vector(0, -0.5, 0));

                        String particleStr = getConfig().getString(key + ".particles", "FLAME").toUpperCase();
                        Particle particle = getParticle(particleStr);
                        String soundStr = getConfig().getString(key + ".sound", "ENTITY_FIREWORK_ROCKET_LAUNCH").toUpperCase();
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
                        }.runTaskTimer(ShootingStarPlugin.this, 0L, 2L);

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!droppedItem.isValid() || droppedItem.isOnGround()) {
                                    cancel();
                                    return;
                                }
                                droppedItem.getWorld().playSound(droppedItem.getLocation(), sound, 1.0f, 1.0f);
                            }
                        }.runTaskTimer(ShootingStarPlugin.this, 0L, 2L);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 40L);
    }

    public void clearLocalData() {
        localData.clear();
        getLogger().info("Local data cleared.");
    }

    public void loadLocalData() {
        ConfigurationSection root = getConfig().getConfigurationSection("");
        if (root != null) {
            localData.put("configKeys", root.getKeys(false));
            getLogger().info("Local data loaded: " + localData);
        } else {
            getLogger().warning("No configuration found to load local data.");
        }
    }

    private static Particle getParticle(String particleStr) {
        try {
            return Particle.valueOf(particleStr);
        } catch (Exception e) {
            return Particle.FLAME;
        }
    }

    private static Sound getSound(String soundStr) {
        try {
            return Sound.valueOf(soundStr);
        } catch (Exception e) {
            return Sound.ENTITY_FIREWORK_ROCKET_LAUNCH;
        }
    }

    private Location getRandomLocationAround(Location center, int radius) {
        int offsetX = random.nextInt(radius * 2 + 1) - radius;
        int offsetY = random.nextInt(radius * 2 + 1) - radius;
        int offsetZ = random.nextInt(radius * 2 + 1) - radius;
        return center.clone().add(offsetX, offsetY, offsetZ);
    }
}

