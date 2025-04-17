package com.shootingstar.quesadilla;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ConfigManager {

    private final JavaPlugin plugin;
    private final Map<String, StarConfig> starConfigurations = new HashMap<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs(FileConfiguration starsCfg) {
        plugin.getLogger().info("Loading star definitions from stars.yml...");
        starConfigurations.clear();

        if (starsCfg == null) {
            plugin.getLogger().severe("Received null configuration for stars.yml. Cannot load stars.");
            return;
        }

        ConfigurationSection rootSection = starsCfg.getConfigurationSection("");
        if (rootSection == null) {
            plugin.getLogger().warning("stars.yml root section is null or file is empty.");
            return;
        }

        java.util.Set<String> foundKeys = rootSection.getKeys(false);
        plugin.getLogger().info("Found " + foundKeys.size() + " potential star definition(s) in stars.yml: " + foundKeys);

        int loadedCount = 0;
        for (String key : foundKeys) {
            plugin.getLogger().fine("Processing potential star key: '" + key + "'");


            ConfigurationSection starSection = rootSection.getConfigurationSection(key);

            if (starSection == null) {
                plugin.getLogger().warning("Key '" + key + "' in stars.yml is not a valid configuration section. Skipping.");
                continue;
            }

            try {
                plugin.getLogger().fine("Attempting to create StarConfig for: '" + key + "'");
                StarConfig config = new StarConfig(key, starSection);
                plugin.getLogger().fine("StarConfig created successfully for: '" + key + "'");

                String lowerCaseKey = key.toLowerCase();
                starConfigurations.put(lowerCaseKey, config);
                plugin.getLogger().fine("Stored StarConfig with lowercase key: '" + lowerCaseKey + "'");
                loadedCount++;

            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load star definition '" + key + "' from stars.yml: " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "An unexpected error occurred while creating StarConfig for key '" + key + "' from stars.yml", e);
            }
        }

        plugin.getLogger().info("Finished loading star definitions. Successfully loaded " + loadedCount + " star(s).");
        plugin.getLogger().fine("Final star map keys (lowercase): " + starConfigurations.keySet());
    }
    public Collection<StarConfig> getAllStarConfigs() {
        return starConfigurations.values();
    }

    public StarConfig getStarConfig(String key) {
        if (key == null) return null;
        return starConfigurations.get(key.toLowerCase());
    }

    public Collection<String> getAllStarConfigKeys() {
        return starConfigurations.values().stream()
                .map(StarConfig::getKey)
                .collect(Collectors.toList());
    }
}