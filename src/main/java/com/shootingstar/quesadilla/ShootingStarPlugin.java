package com.shootingstar.quesadilla;

import org.bukkit.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;

import com.shootingstar.quesadilla.commands.CommandManager;

public class ShootingStarPlugin extends JavaPlugin {

    private final Random random = new Random();
    private ConfigManager configManager;
    private boolean worldGuardAvailable = false;
    private BukkitTask mainSpawningTask = null;
    private File starsFile;
    private FileConfiguration starsConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadStarsConfig();
        StarConfig.initializeLogger(this);
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfigs(getStarsConfig());
        initializeWorldGuardHook();
        registerCommands();
        startSpawningTask();
        getLogger().info("ShootingStarPlugin v" + getDescription().getVersion() + " enabled successfully!");
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("ShootingStarPlugin disabled.");
    }

    private void initializeWorldGuardHook() {
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                WorldGuard.getInstance();
                worldGuardAvailable = true;
                getLogger().info("WorldGuard found and hooked successfully. Region protection enabled.");
            } catch (NoClassDefFoundError e) {
                getLogger().warning("WorldGuard plugin detected, but failed to hook correctly (likely a version mismatch or missing WorldEdit?). Region protection will be disabled.");
                worldGuardAvailable = false;
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "An unexpected error occurred while trying to hook into WorldGuard. Region protection disabled.", e);
                worldGuardAvailable = false;
            }
        } else {
            getLogger().info("WorldGuard not found. Region protection for block damage is disabled.");
            worldGuardAvailable = false;
        }
    }

    private void registerCommands() {
        PluginCommand command = this.getCommand("sh");
        if (command != null) {
            command.setExecutor(new CommandManager(this));
            getLogger().info("Command /sh registered successfully.");
        } else {
            getLogger().severe("FATAL: Could not register command 'sh'! Please check your plugin.yml configuration.");
        }
    }

    private void startSpawningTask() {
        if (mainSpawningTask != null && !mainSpawningTask.isCancelled()) {
            mainSpawningTask.cancel();
        }
        long delay = getConfig().getLong("settings.task.initial-delay-ticks", 1200L);
        long period = getConfig().getLong("settings.task.period-ticks", 600L);
        int spawnCheckRadius = getConfig().getInt("settings.task.spawn-check-radius", 30);
        if (period < 20L) {
            getLogger().warning("Task period is very short ("+ period +" ticks). Setting to 20 ticks (1 second) to prevent performance issues.");
            period = 20L;
        }
        mainSpawningTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (getServer().getOnlinePlayers().isEmpty()) return;
                for (StarConfig starConfig : configManager.getAllStarConfigs()) {
                    World configuredWorld = Bukkit.getWorld(starConfig.getWorldName());
                    if (configuredWorld == null) continue;
                    for (Player player : configuredWorld.getPlayers()) {
                        if (player.getGameMode() == GameMode.SPECTATOR) continue;
                        long currentTime = player.getWorld().getTime();
                        String timeOption = starConfig.getTimeOption();
                        if (("night".equals(timeOption) && (currentTime < 12500 || currentTime > 23500)) || ("day".equals(timeOption) && (currentTime >= 12500 && currentTime <= 23500))) continue;
                        if (getRandom().nextDouble() * 10.0 > starConfig.getProbability()) continue;
                        Location playerLoc = player.getLocation();
                        Location randomBaseLoc = getRandomSurfaceLocationAround(playerLoc, spawnCheckRadius);
                        if (randomBaseLoc == null) continue;
                        if (starConfig.isSkyRequired() && !hasClearSky(randomBaseLoc)) continue;
                        int altitudeOffset = getRandom().nextInt(starConfig.getMaxAltitude() - starConfig.getMinAltitude() + 1) + starConfig.getMinAltitude();
                        Location spawnLoc = randomBaseLoc.clone().add(0, altitudeOffset, 0);
                        if (spawnLoc.getY() >= configuredWorld.getMaxHeight()) spawnLoc.setY(configuredWorld.getMaxHeight() - 1);
                        if (spawnLoc.getY() < configuredWorld.getMinHeight()) spawnLoc.setY(configuredWorld.getMinHeight());
                        spawnShootingStar(starConfig, spawnLoc);
                    }
                }
            }
        }.runTaskTimer(this, delay, period);
        getLogger().info("Automatic star spawning task started (Delay: " + delay + "t, Period: " + period + "t).");
    }

    public ConfigManager getConfigManager() { return configManager; }
    public Random getRandom() { return random; }
    public boolean isWorldGuardAvailable() { return worldGuardAvailable; }
    public FileConfiguration getStarsConfig() { if (this.starsConfig == null) loadStarsConfig(); return this.starsConfig; }

    public void loadStarsConfig() {
        this.starsFile = new File(getDataFolder(), "stars.yml");
        if (!this.starsFile.exists()) {
            getLogger().info("stars.yml not found, creating default file...");
            saveResource("stars.yml", false);
        }
        this.starsConfig = YamlConfiguration.loadConfiguration(this.starsFile);
        getLogger().info("Loaded star definitions from stars.yml");
    }

    public Optional<Item> spawnShootingStar(StarConfig starConfig, Location spawnLocation) {
        if (starConfig == null) { getLogger().warning("spawnShootingStar called with null StarConfig."); return Optional.empty(); }
        if (spawnLocation == null || spawnLocation.getWorld() == null) { getLogger().warning("spawnShootingStar called with invalid Location for star: " + starConfig.getKey()); return Optional.empty(); }

        World world = spawnLocation.getWorld();
        ItemStack itemStack = new ItemStack(starConfig.getItemMaterial());
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(starConfig.getDisplayName());
            List<String> lore = starConfig.getLore();
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            itemStack.setItemMeta(meta);
        }

        Item droppedItem;
        try {
            droppedItem = world.dropItem(spawnLocation, itemStack);
            droppedItem.setVelocity(new Vector(0, -0.5, 0));

            // --- INICIO LÓGICA CONDICIONAL PARA PICKUP DELAY ---
            boolean applyPickupDelay = false;
            Object explosionConfig = starConfig.getExplosionConfig();

            if (explosionConfig instanceof Number num && num.doubleValue() > 0) {
                applyPickupDelay = true;
            } else if (explosionConfig instanceof String str && str.equalsIgnoreCase("fake")) {
                applyPickupDelay = true;
            }

            if (!applyPickupDelay && starConfig.getDamage() > 0) {
                applyPickupDelay = true;
            }

            if (!applyPickupDelay && starConfig.shouldRemoveItemOnImpact()) {
                applyPickupDelay = true;
            }

            if (applyPickupDelay) {
                droppedItem.setPickupDelay(200); // 10 segundos = 200 ticks
                getLogger().fine("Applied 10s pickup delay to star: " + starConfig.getKey());
            }
            // --- FIN LÓGICA CONDICIONAL ---

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to spawn Item entity for star " + starConfig.getKey() + " at " + spawnLocation, e);
            return Optional.empty();
        }

        Particle particle = starConfig.getParticle();
        Sound sound = starConfig.getSound();
        float particleSpeed = (float) getConfig().getDouble("settings.effects.particle-speed", 0f);
        int particleCount = getConfig().getInt("settings.effects.particle-count", 5);
        float soundVolume = (float) getConfig().getDouble("settings.effects.sound-volume", 1.0);
        float soundPitch = (float) getConfig().getDouble("settings.effects.sound-pitch", 1.0);
        long particleInterval = getConfig().getLong("settings.effects.particle-interval-ticks", 2L);
        long soundInterval = getConfig().getLong("settings.effects.sound-interval-ticks", 4L);
        long impactCheckInterval = getConfig().getLong("settings.impact.check-interval-ticks", 1L);
        long impactCheckDelay = getConfig().getLong("settings.impact.check-delay-ticks", 5L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!droppedItem.isValid() || droppedItem.isDead() || droppedItem.isOnGround()) { cancel(); return; }
                Location current = droppedItem.getLocation(); World itemWorld = current.getWorld();
                if (itemWorld != null) itemWorld.spawnParticle(particle, current, particleCount, 0.3, 0.5, 0.3, particleSpeed); else cancel();
            }
        }.runTaskTimer(this, 0L, particleInterval);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!droppedItem.isValid() || droppedItem.isDead() || droppedItem.isOnGround()) { cancel(); return; }
                Location current = droppedItem.getLocation(); World itemWorld = current.getWorld();
                if (itemWorld != null) itemWorld.playSound(current, sound, soundVolume, soundPitch); else cancel();
            }
        }.runTaskTimer(this, 0L, soundInterval);

        new BukkitRunnable() {
            @Override
            public void run() {
                boolean impactConditionMet = droppedItem.isValid() && !droppedItem.isDead() && (droppedItem.isOnGround() || droppedItem.getLocation().getY() <= droppedItem.getWorld().getMinHeight() + 0.5);
                if (impactConditionMet) {
                    Location impactLoc = droppedItem.getLocation().clone(); World impactWorld = impactLoc.getWorld();
                    ItemStack finalItemStack = droppedItem.getItemStack().clone(); boolean shouldRemoveItem = starConfig.shouldRemoveItemOnImpact();
                    if (!shouldRemoveItem) droppedItem.remove();
                    if (impactWorld == null) { getLogger().warning("Impact world became null for star " + starConfig.getKey()); if (droppedItem.isValid()) droppedItem.remove(); cancel(); return; }
                    Object explosionConfig = starConfig.getExplosionConfig(); double damage = starConfig.getDamage();
                    boolean damageBlocksConfig = starConfig.shouldDamageBlocks(); boolean explosionProcessed = false;
                    if (explosionConfig instanceof Number) {
                        double explosionPower = ((Number) explosionConfig).doubleValue();
                        if (explosionPower > 0) {
                            boolean allowBlockDamage = damageBlocksConfig;
                            if (damageBlocksConfig && isWorldGuardAvailable()) {
                                allowBlockDamage = canBreakBlocksWithWG(impactLoc);
                                if (!allowBlockDamage && damageBlocksConfig) getLogger().fine("WorldGuard prevented block damage (impact task) for star " + starConfig.getKey() + " at " + formatLocation(impactLoc));
                            }
                            impactWorld.createExplosion(impactLoc, (float) explosionPower, false, allowBlockDamage); explosionProcessed = true;
                        } else explosionProcessed = true;
                    } else if (explosionConfig instanceof String) {
                        String explosionStr = ((String) explosionConfig).toLowerCase();
                        if (explosionStr.equals("fake")) { impactWorld.spawnParticle(Particle.EXPLOSION_LARGE, impactLoc, 1); impactWorld.playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.0F); explosionProcessed = true;
                        } else if (explosionStr.equals("false")) explosionProcessed = true;
                    }
                    if (!explosionProcessed && explosionConfig == null) { impactWorld.spawnParticle(Particle.EXPLOSION_LARGE, impactLoc, 1); impactWorld.playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.0F); }
                    if (damage > 0) {
                        double damageRadius = getConfig().getDouble("settings.impact.damage-radius", 3.0); if (damageRadius < 0) damageRadius = 0;
                        Entity damageSource = (Entity) (droppedItem.isValid() ? droppedItem : null);
                        for (Entity entity : impactWorld.getNearbyEntities(impactLoc, damageRadius, damageRadius, damageRadius)) {
                            if (entity instanceof Damageable && !entity.equals(droppedItem) && !entity.isInvulnerable()) ((Damageable) entity).damage(damage, damageSource);
                        }
                    }
                    if (shouldRemoveItem) { if (droppedItem.isValid()) droppedItem.remove(); }
                    else {
                        if (finalItemStack != null && finalItemStack.getType() != Material.AIR) {
                            Item newItem = impactWorld.dropItemNaturally(impactLoc.add(0, 0.1, 0), finalItemStack);
                            if (newItem != null) {
                                newItem.setPickupDelay(20); // Permitir recogida tras 1 segundo
                            }
                        }
                    }
                    cancel();
                } else if (!droppedItem.isValid() || droppedItem.isDead()) cancel();
            }
        }.runTaskTimer(this, impactCheckDelay, impactCheckInterval);
        return Optional.of(droppedItem);
    }

    public void reloadPluginConfig() {
        getLogger().info("Reloading ShootingStar configuration...");
        reloadConfig();
        loadStarsConfig();
        if (configManager != null) {
            configManager.loadConfigs(getStarsConfig());
            startSpawningTask();
            getLogger().info("Configurations reloaded (config.yml & stars.yml) and task restarted!");
        } else {
            getLogger().warning("ConfigManager not initialized, cannot reload configurations properly.");
        }
    }

    private Location getRandomSurfaceLocationAround(Location center, int radius) {
        if (center == null || center.getWorld() == null) return null;
        World world = center.getWorld();
        for (int i = 0; i < 10; i++) {
            int offsetX = getRandom().nextInt(radius * 2 + 1) - radius; int offsetZ = getRandom().nextInt(radius * 2 + 1) - radius;
            Location potentialLoc = center.clone().add(offsetX, 0, offsetZ);
            int highestY = world.getHighestBlockYAt(potentialLoc, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (highestY < world.getMinHeight() || highestY >= world.getMaxHeight() -1) continue;
            potentialLoc.setY(highestY);
            Material blockBelowType = world.getBlockAt(potentialLoc).getType();
            if (!isUnsafeGround(blockBelowType)) { potentialLoc.setY(highestY + 1.0); return potentialLoc; }
        }
        return null;
    }

    private boolean isUnsafeGround(Material material) { return material == Material.LAVA || material == Material.FIRE || material == Material.MAGMA_BLOCK || material == Material.CACTUS || material == Material.VOID_AIR || material == Material.POWDER_SNOW; }
    private boolean hasClearSky(Location location) {
        World world = location.getWorld(); if (world == null) return false; int x = location.getBlockX(); int z = location.getBlockZ();
        for (int y = location.getBlockY() + 1; y < world.getMaxHeight(); y++) { if (world.getBlockAt(x, y, z).getType().isOccluding()) return false; } return true;
    }

    public boolean canBreakBlocksWithWG(Location loc) {
        if (!worldGuardAvailable || loc == null || loc.getWorld() == null) return true;
        String flagNameInput = getConfig().getString("settings.worldguard.explosion-check-flag", "tnt").toLowerCase().trim();
        String flagNameToUse;
        final Set<String> validFlags = Set.of("tnt", "other-explosion", "creeper-explosion", "none");
        if (validFlags.contains(flagNameInput)) { flagNameToUse = flagNameInput; }
        else { getLogger().warning("Invalid value found for 'settings.worldguard.explosion-check-flag': '" + flagNameInput + "'. Using default value 'tnt'. Valid options are: " + validFlags); flagNameToUse = "tnt"; }
        if (flagNameToUse.equals("none")) return true;
        try {
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
            Flag<?> flag = registry.get(flagNameToUse);
            if (flag instanceof StateFlag stateFlag) {
                RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                com.sk89q.worldedit.util.Location wgLoc = BukkitAdapter.adapt(loc); RegionQuery query = container.createQuery();
                boolean allowed = query.testState(wgLoc, null, stateFlag);
                return allowed;
            } else { getLogger().warning("Could not find WorldGuard StateFlag named '" + flagNameToUse + "' in the registry. Denying block damage as a precaution."); return false; }
        } catch (Exception e) { getLogger().log(Level.WARNING, "Error while checking WorldGuard flag '" + flagNameToUse + "' permission at " + formatLocation(loc), e); return false; }
    }

    private String formatLocation(Location loc) { if (loc == null) return "N/A"; return String.format("%s[%.1f, %.1f, %.1f]", loc.getWorld() != null ? loc.getWorld().getName() : "UnknownWorld", loc.getX(), loc.getY(), loc.getZ()); }

}