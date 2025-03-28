package com.shootingstar.quesadilla;

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

        // Tarea que se ejecuta cada 40 ticks para buscar jugadores y generar estrellas según configuración
        new BukkitRunnable() {
            @Override
            public void run() {
                // Recorre todos los jugadores en línea
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // Obtiene el tiempo actual del mundo del jugador
                    long currentTime = player.getWorld().getTime();

                    // Recorre cada clave configurada en config.yml (cada tipo de estrella fugaz)
                    for (String key : getConfig().getKeys(false)) {
                        // Se evalúa la opción "time" para este tipo de estrella (default: "night")
                        String timeSetting = getConfig().getString(key + ".time", "night").toLowerCase();
                        // Si se requiere que se ejecute solo de noche y no se cumple, se salta
                        if (timeSetting.equals("night") && (currentTime < 13000 || currentTime > 23000)) {
                            continue;
                        }
                        // Si se requiere que se ejecute solo de día y no se cumple, se salta
                        if (timeSetting.equals("day") && (currentTime >= 13000 && currentTime <= 23000)) {
                            continue;
                        }
                        
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

                        // Obtiene el tipo de partícula configurado
                        String particleStr = getConfig().getString(key + ".particles", "FLAME").toUpperCase();
                        final Particle particle;
                        try {
                            particle = Particle.valueOf(particleStr);
                        } catch(Exception e) {
                            particle = Particle.FLAME;
                        }
                        // Tarea para generar la columna de partículas que sigue al ítem
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
                        }.runTaskTimer(ShootingStarPlugin.this, 0L, 1L);

                        // Obtiene el tipo de sonido configurado
                        String soundStr = getConfig().getString(key + ".sound", "ENTITY_FIREWORK_ROCKET_LAUNCH").toUpperCase();
                        final Sound sound;
                        try {
                            sound = Sound.valueOf(soundStr);
                        } catch(Exception e) {
                            sound = Sound.ENTITY_FIREWORK_ROCKET_LAUNCH;
                        }
                        // Tarea para reproducir el sonido siguiendo al ítem
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!droppedItem.isValid() || droppedItem.isOnGround()) {
                                    cancel();
                                    return;
                                }
                                Location current = droppedItem.getLocation();
                                current.getWorld().playSound(current, sound, 1.0f, 1.0f);
                            }
                        }.runTaskTimer(ShootingStarPlugin.this, 0L, 1L);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 40L);
    }

    // Método para obtener una ubicación aleatoria en un cubo de radio dado alrededor de la ubicación central
    private Location getRandomLocationAround(Location center, int radius) {
        int offsetX = random.nextInt(radius * 2 + 1) - radius;
        int offsetY = random.nextInt(radius * 2 + 1) - radius;
        int offsetZ = random.nextInt(radius * 2 + 1) - radius;
        return center.clone().add(offsetX, offsetY, offsetZ);
    }
}
