package com.shootingstar.quesadilla;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
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

    @Override
    public void onEnable() {
        // Genera el archivo config.yml si no existe (con valores predeterminados)
        saveDefaultConfig();

        // Registra el executor para el comando /sh (definido en una clase separada)
        if (getCommand("sh") != null) {
            getCommand("sh").setExecutor(new com.shootingstar.quesadilla.commands.ShCommandExecutor(this));
        }

        // Tarea principal: cada 40 ticks se busca generar estrellas para cada jugador
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // Recorre cada sección configurada (cada tipo de estrella)
                    ConfigurationSection section = getConfig().getConfigurationSection("");
                    if (section == null) continue;
                    for (String key : section.getKeys(false)) {
                        // Condición de tiempo: "night", "day" o "all"
                        String timeOption = getConfig().getString(key + ".time", "night").toLowerCase();
                        long worldTime = player.getWorld().getTime();
                        boolean validTime = false;
                        switch (timeOption) {
                            case "night":
                                validTime = (worldTime >= 13000 && worldTime <= 23000);
                                break;
                            case "day":
                                validTime = (worldTime < 13000 || worldTime > 23000);
                                break;
                            case "all":
                                validTime = true;
                                break;
                        }
                        if (!validTime) continue;

                        // Probabilidad de generar la estrella
                        double probability = getConfig().getDouble(key + ".probability");
                        if (random.nextDouble() * 10 > probability) continue;

                        // Selecciona un bloque aleatorio en un radio de 15 bloques alrededor del jugador
                        Location randomLoc = getRandomLocationAround(player.getLocation(), 15);
                        // Verifica si se requiere cielo abierto
                        boolean skyRequired = getConfig().getBoolean(key + ".sky");
                        if (skyRequired) {
                            Block block = randomLoc.getBlock();
                            if (block.getRelative(BlockFace.UP).getType() != Material.AIR) continue;
                        }

                        // Calcula la altitud de caída usando el rango definido (por ejemplo, "25-50")
                        String altitudeRange = getConfig().getString(key + ".altitude", "25-50");
                        String[] parts = altitudeRange.split("-");
                        int minAlt = Integer.parseInt(parts[0].trim());
                        int maxAlt = Integer.parseInt(parts[1].trim());
                        int offsetY = random.nextInt(maxAlt - minAlt + 1) + minAlt;
                        Location spawnLoc = randomLoc.clone().add(0, offsetY, 0);

                        // Crea el ItemStack según la configuración (material, nombre y lore)
                        String itemStr = getConfig().getString(key + ".item", "NETHER_STAR");
                        Material material = Material.matchMaterial(itemStr.toUpperCase());
                        if (material == null) continue;
                        ItemStack itemStack = new ItemStack(material);
                        ItemMeta meta = itemStack.getItemMeta();
                        if (meta != null) {
                            String name = ChatColor.translateAlternateColorCodes('&', getConfig().getString(key + ".name", "Estrella Fugaz"));
                            meta.setDisplayName(name);
                            List<String> loreConfig = getConfig().getStringList(key + ".lore");
                            List<String> lore = new ArrayList<>();
                            for (String line : loreConfig) {
                                lore.add(ChatColor.translateAlternateColorCodes('&', line));
                            }
                            meta.setLore(lore);
                            itemStack.setItemMeta(meta);
                        }
                        // Lanza el ítem con una velocidad descendente
                        Item droppedItem = spawnLoc.getWorld().dropItem(spawnLoc, itemStack);
                        droppedItem.setVelocity(new Vector(0, -0.5, 0));

                        // Obtiene el tipo de partícula configurado
                        String particleStr = getConfig().getString(key + ".particles", "FLAME").toUpperCase();
                        final Particle particle;
                        try {
                            particle = Particle.valueOf(particleStr);
                        } catch (Exception e) {
                            particle = Particle.FLAME;
                        }

                        // Obtiene el sonido configurado
                        String soundStr = getConfig().getString(key + ".sound", "ENTITY_FIREWORK_ROCKET_LAUNCH").toUpperCase();
                        final Sound sound;
                        try {
                            sound = Sound.valueOf(soundStr);
                        } catch (Exception e) {
                            sound = Sound.ENTITY_FIREWORK_ROCKET_LAUNCH;
                        }

                        // Configuración de luz: solo se usará si light está en true
                        final boolean spawnLight = getConfig().getBoolean(key + ".light", false);
                        final int lightLevel = getConfig().getInt(key + ".lightlevel", 7);
                        final boolean[] lightSpawned = { false };

                        // Tarea de partículas (se ejecuta cada tick)
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!droppedItem.isValid()) {
                                    cancel();
                                    return;
                                }
                                // Al tocar el suelo se procede a crear la luz (si está activada)
                                if (droppedItem.isOnGround()) {
                                    if (!lightSpawned[0] && spawnLight) {
                                        lightSpawned[0] = true;
                                        // Ubicación de la luz: bloque directamente sobre el impacto
                                        Location impactLoc = droppedItem.getLocation().clone();
                                        impactLoc.setY(impactLoc.getBlockY() + 1);
                                        Block lightBlock = impactLoc.getBlock();
                                        // En Spigot 1.20.4 usamos el bloque LIGHT
                                        lightBlock.setType(Material.LIGHT);
                                        
                                        try {
                                            Object data = lightBlock.getBlockData();
                                            Method setLevelMethod = data.getClass().getMethod("setLevel", int.class);
                                            setLevelMethod.invoke(data, lightLevel);
                                            lightBlock.setBlockData((org.bukkit.block.data.BlockData) data);
                                            // Tarea de desvanecimiento: espera 20 segundos, luego fade gradual en 2.5 segundos (50 ticks)
                                            new BukkitRunnable() {
                                                int fadeTicks = 0;
                                                @Override
                                                public void run() {
                                                    fadeTicks++;
                                                    int newLevel = (int) Math.max(lightLevel - ((double) lightLevel * fadeTicks / 50.0), 0);
                                                    try {
                                                        setLevelMethod.invoke(data, newLevel);
                                                        lightBlock.setBlockData((org.bukkit.block.data.BlockData) data);
                                                    } catch(Exception ex) {
                                                        ex.printStackTrace();
                                                    }
                                                    if (fadeTicks >= 50) {
                                                        lightBlock.setType(Material.AIR);
                                                        cancel();
                                                    }
                                                }
                                            }.runTaskTimer(ShootingStarPlugin.this, 20 * 20L, 1L);
                                        } catch(Exception ex) {
                                            // Fallback: elimina el bloque después de 22.5 segundos
                                            new BukkitRunnable() {
                                                @Override
                                                public void run() {
                                                    lightBlock.setType(Material.AIR);
                                                }
                                            }.runTaskLater(ShootingStarPlugin.this, 450L);
                                        }
                                    }
                                    cancel();
                                    return;
                                }
                                // Genera columna de partículas acompañando al ítem
                                Location current = droppedItem.getLocation();
                                for (int i = 0; i < 5; i++) {
                                    Location particleLoc = current.clone().subtract(0, i * 0.5, 0);
                                    current.getWorld().spawnParticle(particle, particleLoc, 5, 0.2, 0.2, 0.2, 0);
                                }
                            }
                        }.runTaskTimer(ShootingStarPlugin.this, 0L, 1L);

                        // Tarea para reproducir el sonido cada 5 ticks mientras el ítem cae
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!droppedItem.isValid() || droppedItem.isOnGround()) {
                                    cancel();
                                    return;
                                }
                                spawnLoc.getWorld().playSound(droppedItem.getLocation(), sound, 1.0f, 1.0f);
                            }
                        }.runTaskTimer(ShootingStarPlugin.this, 0L, 5L);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 40L);
    }

    // Método para forzar la caída de una estrella (invocado por el comando /sh shoot)
    public void forceSpawnStar(Player player, String key) {
        if (!getConfig().contains(key)) {
            player.sendMessage(ChatColor.RED + "El tipo de estrella '" + key + "' no existe.");
            return;
        }
        Location loc = player.getLocation();
        String altitudeRange = getConfig().getString(key + ".altitude", "25-50");
        String[] parts = altitudeRange.split("-");
        int minAlt = Integer.parseInt(parts[0].trim());
        int maxAlt = Integer.parseInt(parts[1].trim());
        int offsetY = random.nextInt(maxAlt - minAlt + 1) + minAlt;
        Location spawnLoc = loc.clone().add(0, offsetY, 0);

        String itemStr = getConfig().getString(key + ".item", "NETHER_STAR");
        Material material = Material.matchMaterial(itemStr.toUpperCase());
        if (material == null) {
            player.sendMessage(ChatColor.RED + "Material inválido en la configuración.");
            return;
        }
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            String name = ChatColor.translateAlternateColorCodes('&', getConfig().getString(key + ".name", "Estrella Fugaz"));
            meta.setDisplayName(name);
            List<String> loreConfig = getConfig().getStringList(key + ".lore");
            List<String> lore = new ArrayList<>();
            for (String line : loreConfig) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);
            itemStack.setItemMeta(meta);
        }
        Item droppedItem = spawnLoc.getWorld().dropItem(spawnLoc, itemStack);
        droppedItem.setVelocity(new Vector(0, -0.5, 0));

        player.sendMessage(ChatColor.GREEN + "¡Estrella " + key + " lanzada!");
    }

    // Método para recargar la configuración del plugin (invocado por /sh reload)
    public void reloadPluginConfig(org.bukkit.command.CommandSender sender) {
        reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "¡Configuración recargada!");
    }

    // Método auxiliar para obtener una ubicación aleatoria en un cubo de radio dado alrededor del centro
    private Location getRandomLocationAround(Location center, int radius) {
        int offsetX = random.nextInt(radius * 2 + 1) - radius;
        int offsetY = random.nextInt(radius * 2 + 1) - radius;
        int offsetZ = random.nextInt(radius * 2 + 1) - radius;
        return center.clone().add(offsetX, offsetY, offsetZ);
    }
                                                            }
                                                              
