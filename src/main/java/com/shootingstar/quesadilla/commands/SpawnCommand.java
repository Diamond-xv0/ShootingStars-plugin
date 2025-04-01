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

    /**
     * Formatos aceptados:
     *
     * - Si se ejecuta por un jugador:
     *   • /sh spawn <name>                      → se usa la ubicación del ejecutor (x, y y z como referencia).
     *   • /sh spawn <name> <player>             → se usa la ubicación del jugador especificado.
     *   • /sh spawn <name> <x> <y> <z>           → se usan coordenadas (x, y, z), luego se le suma la altitud de la config.
     *
     * - Si se ejecuta desde la consola:
     *   • /sh spawn <name> <player>             → se usa la ubicación del jugador especificado.
     *   • /sh spawn <name> <x> <y> <z> <world>     → se usan coordenadas junto con el mundo indicado.
     *
     * En todos los casos, la ubicación de referencia (incluyendo su Y) se usa para tomar x, y y z,
     * y a la coordenada Y se le añade un offset aleatorio obtenido del rango definido en la configuración.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Verifica el permiso.
        if (!sender.hasPermission("shootingstars.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Use: /sh spawn <name> [<player> | <x> <y> <z> [<world>]]");
            return true;
        }

        // El primer argumento es la clave de la configuración de la estrella.
        String starConfigKey = args[0];
        Location baseLocation = null;
        World world = null;

        // Caso 1: Solo se pasa <name>.
        if (args.length == 1) {
            if (sender instanceof Player) {
                Player playerSender = (Player) sender;
                baseLocation = playerSender.getLocation();
                world = playerSender.getWorld();
            } else {
                sender.sendMessage(ChatColor.RED + "In console you need to specify a location with the player or coordinates.");
                return true;
            }
        }
        // Caso 2: Se pasan 2 argumentos: se asume que el segundo es el nombre de un jugador.
        else if (args.length == 2) {
            if (isNumeric(args[1])) {
                sender.sendMessage(ChatColor.RED + "Incorrect use, for coordinates you must specify <x> <y> <z>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target != null) {
                baseLocation = target.getLocation();
                world = target.getWorld();
            } else {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }
        }
        // Caso 3: Se pasan coordenadas sin especificar mundo (4 argumentos: <name> <x> <y> <z>).
        else if (args.length == 4) {
            try {
                double x = Double.parseDouble(args[1]);
                double y = Double.parseDouble(args[2]); // Se toma la Y de referencia.
                double z = Double.parseDouble(args[3]);
                if (sender instanceof Player) {
                    world = ((Player) sender).getWorld();
                } else {
                    sender.sendMessage(ChatColor.RED + "In console you need to specify the world if you use coordinates");
                    return true;
                }
                baseLocation = new Location(world, x, y, z);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid coordinates, you must use numbers");
                return true;
            }
        }
        // Caso 4: Se pasan coordenadas y mundo (5 argumentos: <name> <x> <y> <z> <world>).
        else if (args.length == 5) {
            try {
                double x = Double.parseDouble(args[1]);
                double y = Double.parseDouble(args[2]); // Se toma la Y de referencia.
                double z = Double.parseDouble(args[3]);
                world = Bukkit.getWorld(args[4]);
                if (world == null) {
                    sender.sendMessage(ChatColor.RED + "World not found: " + args[4]);
                    return true;
                }
                baseLocation = new Location(world, x, y, z);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid coordinates, you must use numbers.");
                return true;
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Incorrect use: /sh spawn <name> [<player> | <x> <y> <z> [<world>]]");
            return true;
        }

        // Se obtiene la sección de configuración de la estrella indicada.
        ConfigurationSection starConfig = plugin.getConfig().getConfigurationSection(starConfigKey);
        if (starConfig == null) {
            sender.sendMessage(ChatColor.RED + "Config not found for star: " + starConfigKey);
            return true;
        }

        // Se leen los parámetros permitidos desde la configuración.
        String itemStr = starConfig.getString("item", "NETHER_STAR");
        Material material = Material.matchMaterial(itemStr.toUpperCase());
        if (material == null) {
            sender.sendMessage(ChatColor.RED + "Invalid material: " + itemStr);
            return true;
        }
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            String displayName = ChatColor.translateAlternateColorCodes('&', starConfig.getString("name", "Estrella Fugaz"));
            meta.setDisplayName(displayName);
            List<String> loreConfig = starConfig.getStringList("lore");
            List<String> lore = new ArrayList<>();
            for (String line : loreConfig) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);
            itemStack.setItemMeta(meta);
        }

        // Se obtiene la altitud a sumar, generando un offset aleatorio dentro del rango definido en la configuración.
        String altitudeRange = starConfig.getString("altitude", "25-50");
        String[] parts = altitudeRange.split("-");
        int minAlt, maxAlt;
        try {
            minAlt = Integer.parseInt(parts[0].trim());
            maxAlt = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            sender.sendMessage(ChatColor.RED + "The selected altitude setting is invalid");
            return true;
        }
        int offsetY = random.nextInt(maxAlt - minAlt + 1) + minAlt;
        // Se construye la ubicación final tomando la ubicación de referencia y sumándole el offset a la Y.
        Location spawnLoc = baseLocation.clone().add(0, offsetY, 0);

        // Se lanza el ítem en el mundo con velocidad descendente.
        Item droppedItem = spawnLoc.getWorld().dropItem(spawnLoc, itemStack);
        droppedItem.setVelocity(new Vector(0, -0.5, 0));

        // Se obtiene el tipo de partícula a usar.
        String particleStr = starConfig.getString("particles", "FLAME").toUpperCase();
        Particle particle;
        try {
            particle = Particle.valueOf(particleStr);
        } catch (Exception e) {
            particle = Particle.FLAME;
        }

        // Tarea que genera partículas mientras la estrella cae.
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
        }.runTaskTimer(plugin, 0L, 2L);

        // Se obtiene el sonido configurado.
        String soundStr = starConfig.getString("sound", "ENTITY_FIREWORK_ROCKET_LAUNCH").toUpperCase();
        Sound sound;
        try {
            sound = Sound.valueOf(soundStr);
        } catch (Exception e) {
            sound = Sound.ENTITY_FIREWORK_ROCKET_LAUNCH;
        }

        // Tarea que reproduce el sonido mientras la estrella cae.
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
        }.runTaskTimer(plugin, 0L, 2L);

        sender.sendMessage(ChatColor.GREEN + "The star '" + starConfigKey + "' was spawned in location, elevated " + offsetY + " blocks.");
        return true;
    }

    // Metodo auxiliar para comprobar si una cadena es numérica.
    private boolean isNumeric(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch(NumberFormatException e) {
            return false;
        }
    }
}
