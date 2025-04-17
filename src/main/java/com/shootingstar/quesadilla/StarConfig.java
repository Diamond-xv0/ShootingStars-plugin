package com.shootingstar.quesadilla;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.stream.Collectors;

public class StarConfig {

    private final String key;
    private final String worldName;
    private final String timeOption;
    private final double probability;
    private final boolean skyRequired;
    private final int minAltitude;
    private final int maxAltitude;
    private final Material itemMaterial;
    private final String displayName;
    private final List<String> lore;
    private final Particle particle;
    private final Sound sound;
    private final Object explosionConfig;
    private final double damage;
    private final boolean damageBlocks;
    private final boolean removeItemOnImpact; // True para desaparecer, False para quedarse

    private static JavaPlugin loggerPlugin;

    public static void initializeLogger(JavaPlugin plugin) {
        loggerPlugin = plugin;
    }

    public StarConfig(String key, ConfigurationSection section) throws IllegalArgumentException {
        if (loggerPlugin == null) {
            throw new IllegalStateException("LoggerPlugin not initialized in StarConfig. Call StarConfig.initializeLogger() first.");
        }

        this.key = key;
        this.worldName = section.getString("world", "world");
        this.timeOption = section.getString("time", "night").toLowerCase();
        this.probability = section.getDouble("probability", 0);
        this.skyRequired = section.getBoolean("sky", true);

        String altitudeRange = section.getString("altitude", "40-50");
        String[] parts = altitudeRange.split("-");
        if (parts.length == 2) {
            try {
                this.minAltitude = Integer.parseInt(parts[0].trim());
                this.maxAltitude = Integer.parseInt(parts[1].trim());
                if (minAltitude > maxAltitude) {
                    throw new IllegalArgumentException("Invalid altitude range (min > max) for key: " + key);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number format in altitude range for key: " + key, e);
            }
        } else {
            throw new IllegalArgumentException("Invalid altitude format (expected 'min-max') for key: " + key);
        }

        String itemStr = section.getString("item", "NETHER_STAR");
        this.itemMaterial = Material.matchMaterial(itemStr.toUpperCase());
        if (this.itemMaterial == null) {
            throw new IllegalArgumentException("Invalid material '" + itemStr + "' for key: " + key);
        }
        this.displayName = ChatColor.translateAlternateColorCodes('&', section.getString("name", "&bFalling Star")); // Default con color
        this.lore = section.getStringList("lore").stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList());

        String particleStr = section.getString("particles", "FLAME").toUpperCase();
        Particle parsedParticle = null;
        try {
            parsedParticle = Particle.valueOf(particleStr);
        } catch (IllegalArgumentException e) {
            loggerPlugin.getLogger().warning("Invalid particle type '" + particleStr + "' for key '" + key + "'. Defaulting to FLAME.");
            parsedParticle = Particle.FLAME;
        }
        this.particle = parsedParticle;

        String soundStr = section.getString("sound", "ENTITY_FIREWORK_ROCKET_LAUNCH").toUpperCase();
        Sound parsedSound = null;
        try {
            parsedSound = Sound.valueOf(soundStr);
        } catch (IllegalArgumentException e) {
            loggerPlugin.getLogger().warning("Invalid sound type '" + soundStr + "' for key '" + key + "'. Defaulting to ENTITY_FIREWORK_ROCKET_LAUNCH.");
            parsedSound = Sound.ENTITY_FIREWORK_ROCKET_LAUNCH;
        }
        this.sound = parsedSound;

        this.explosionConfig = section.get("explosion");
        this.damage = section.getDouble("damage", 0.0);
        this.damageBlocks = section.getBoolean("damage_blocks", false);
        this.removeItemOnImpact = section.getBoolean("remove_item_on_impact", false);
    }

    public String getKey() { return key; }
    public String getWorldName() { return worldName; }
    public String getTimeOption() { return timeOption; }
    public double getProbability() { return probability; }
    public boolean isSkyRequired() { return skyRequired; }
    public int getMinAltitude() { return minAltitude; }
    public int getMaxAltitude() { return maxAltitude; }
    public Material getItemMaterial() { return itemMaterial; }
    public String getDisplayName() { return displayName; }
    public List<String> getLore() { return lore; }
    public Particle getParticle() { return particle; }
    public Sound getSound() { return sound; }
    public Object getExplosionConfig() { return explosionConfig; }
    public double getDamage() { return damage; }
    public boolean shouldDamageBlocks() { return damageBlocks; }
    public boolean shouldRemoveItemOnImpact() { return removeItemOnImpact; }
}