package com.shootingstar.quesadilla;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    private Random random = new Random();

    @Override
    public void onEnable() {
        // Genera el archivo config.yml si no existe
        saveDefaultConfig();

        // Registra el comando /shreload usando la clase ReloadCommand
        Objects.requireNonNull(this.getCommand("shreload")).setExecutor(new ReloadCommand(this));

        // Tarea que se ejecuta cada 40 ticks para buscar jugadores y generar estrellas
        new BukkitRunnable() {
            @Override
            public void run() {
                // Lee la opción configurable "time" (default: "night")
                String timeOption = getConfig().getString(".time", "night").toLowerCase();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    // Obtiene el tiempo del mundo actual y lo asigna a 'currentWorldTime'
                    long currentWorldTime = player.getWorld().getTime();

                    // Comprueba el tiempo según la opción ".time"
                    if (timeOption.equals("night")) {
                        // Sólo de noche: ticks entre 13000 y 23000
                        if (currentWorldTime < 13000 || currentWorldTime > 23000) {
                            continue;
                        }
                    } else if (timeOption.equals("day")) {
                        // Sólo de día: fuera del rango de 13000 a 23000
                        if (currentWorldTime >= 13000 && currentWorldTime <= 23000) {
                            continue;
                        }
                    }

                    // Recorre cada sección configurada (cada tipo de estrella fugaz)
                    ConfigurationSection section = getConfig().getConfigurationSection("");
                    if (section == null) continue;
                    for (String key : section.getKeys(false)) {
                        double probability = getConfig().getDouble(key + ".probability");
                        // Se usa un valor aleatorio entre 0 y 10: valores mayores en la config incrementan la chance
                        if (random.nextDouble() * 10 > probability) {
                            continue;
                        }
                        // Selecciona un bloque aleatorio en un radio de 15 bloques alrededor del jugador
                        Location randomLoc = getRandomLocationAround(player.getLocation(), 15);
                        // Si se requiere cielo abierto, se comprueba que el bloque tenga aire sobre él
                        boolean skyRequired = getConfig().getBoolean(key + ".sky");
                        if (skyRequired) {
                            Block block = randomLoc.getBlock();
                            if (block.getRelative(BlockFace.UP).getType() != Material.AIR) {
                                continue;
                            }
                        }
                        // Calcula la altitud de caída seleccionando un valor aleatorio dentro del rango definido
                        String altitudeRange = getConfig().getString(key + ".altitude", "25-50");
                        String[] parts = altitudeRange.split("-");
                        int minAlt = Integer.parseInt(parts[0].trim());
                        int maxAlt = Integer.parseInt(parts[1].trim());
                        int offsetY = random.nextInt(maxAlt - minAlt + 1) + minAlt;
                        Location spawnLoc = randomLoc.clone().add(0, offsetY, 0);

                        // Crea el ItemStack según la configuración
                        String itemStr = getConfig().getString(key + ".item", "NETHER_STAR");
                        Material material = Material.matchMaterial(itemStr.toUpperCase());
                        if (material == null) continue;
                        ItemStack itemStack = new ItemStack(material);

                        // Configura el nombre y lore
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

                        // Lanza el ítem en el mundo con una velocidad descendente
                        Item droppedItem = spawnLoc.getWorld().dropItem(spawnLoc, itemStack);
                        droppedItem.setVelocity(new Vector(0, -0.5, 0));

                        // Se obtiene el tipo de partícula configurado
                        String particleStr = getConfig().getString(key + ".particles", "FLAME").toUpperCase();
                        Particle particle;
                        try {
                            particle = Particle.valueOf(particleStr);
                        } catch(Exception e) {
                            particle = Particle.FLAME;
                        }

                        // Tarea que genera partículas mientras la estrella cae
                        Particle finalParticle = particle;
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
                                    current.getWorld().spawnParticle(finalParticle, particleLoc, 5, 0.2, 0.2, 0.2, 0);
                                }
                            }
                        }.runTaskTimer(ShootingStarPlugin.this, 0L, 2L);

                        // Configura el sonido a reproducir
                        String soundStr = getConfig().getString(key + ".sound", "ENTITY_FIREWORK_ROCKET_LAUNCH").toUpperCase();
                        Sound sound;
                        try {
                            sound = Sound.valueOf(soundStr);
                        } catch(Exception e) {
                            sound = Sound.ENTITY_FIREWORK_ROCKET_LAUNCH;
                        }
                        Sound finalSound = sound;
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!droppedItem.isValid() || droppedItem.isOnGround()) {
                                    cancel();
                                    return;
                                }
                                droppedItem.getWorld().playSound(droppedItem.getLocation(), finalSound, 1.0f, 1.0f);
                            }
                        }.runTaskTimer(ShootingStarPlugin.this, 0L, 2L);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 40L);
    }

    // Metodo para obtener una ubicación aleatoria en un cubo de radio dado alrededor de la ubicación central
    private Location getRandomLocationAround(Location center, int radius) {
        int offsetX = random.nextInt(radius * 2 + 1) - radius;
        int offsetY = random.nextInt(radius * 2 + 1) - radius;
        int offsetZ = random.nextInt(radius * 2 + 1) - radius;
        return center.clone().add(offsetX, offsetY, offsetZ);
    }
}
