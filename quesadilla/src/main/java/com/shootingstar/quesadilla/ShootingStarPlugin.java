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
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
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

        // Registra el executor para el comando /shoting
        PluginCommand cmd = getCommand("shoting");
        if (cmd != null) {
            cmd.setExecutor(new ShootingCommandExecutor(this));
        }

        // Tarea que se ejecuta cada 40 ticks para buscar jugadores y generar estrellas (sólo de noche)
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // Solo durante la noche (por ejemplo, entre 13000 y 23000 ticks)
                    long time = player.getWorld().getTime();
                    if (time < 13000 || time > 23000) {
                        continue;
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
                        // Se obtiene el tipo de partícula configurado
                        String particleStr = getConfig().getString(key + ".particles", "FLAME").toUpperCase();
                        final Particle particle;
                        Particle particle1;
                        try {
                            particle1 = Particle.valueOf(particleStr);
                        } catch(Exception e) {
                            particle1 = Particle.FLAME;
                        }

                        // Se inicia una tarea que genera una columna de partículas "acompañando" la estrella en caída
                        particle = particle1;
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                // Cancela si el ítem ya no existe o si ha tocado el suelo
                                if (!droppedItem.isValid() || droppedItem.isOnGround()) {
                                    cancel();
                                    return;
                                }
                                Location current = droppedItem.getLocation();
                                // Genera partículas en una columna descendente (ajusta la cantidad y separación según sea necesario)
                                for (int i = 0; i < 5; i++) {
                                    Location particleLoc = current.clone().subtract(0, i * 0.5, 0);
                                    current.getWorld().spawnParticle(particle, particleLoc, 5, 0.2, 0.2, 0.2, 0);
                                }
                            }
                        }.runTaskTimer(ShootingStarPlugin.this, 0L, 1L);

                        // Reproduce el sonido configurado para los jugadores cercanos
                        String soundStr = getConfig().getString(key + ".sound", "ENTITY_FIREWORK_ROCKET_LAUNCH").toUpperCase();
                        Sound sound;
                        try {
                            sound = Sound.valueOf(soundStr);
                        } catch(Exception e) {
                            sound = Sound.ENTITY_FIREWORK_ROCKET_LAUNCH;
                        }
                        spawnLoc.getWorld().playSound(spawnLoc, sound, 1.0f, 1.0f);
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

    // Método para forzar la caída de una estrella de un tipo dado (ignora todas las condiciones)
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

        String particleStr = getConfig().getString(key + ".particles", "FLAME").toUpperCase();
        Particle particle;
        try {
            particle = Particle.valueOf(particleStr);
        } catch(Exception e) {
            particle = Particle.FLAME;
        }

        // Tarea para generar la columna descendente de partículas acompañando la estrella
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
        }.runTaskTimer(this, 0L, 1L);

        String soundStr = getConfig().getString(key + ".sound", "ENTITY_FIREWORK_ROCKET_LAUNCH").toUpperCase();
        Sound sound;
        try {
            sound = Sound.valueOf(soundStr);
        } catch(Exception e) {
            sound = Sound.ENTITY_FIREWORK_ROCKET_LAUNCH;
        }
        spawnLoc.getWorld().playSound(spawnLoc, sound, 1.0f, 1.0f);
        player.sendMessage(ChatColor.GREEN + "¡Estrella " + key + " lanzada!");
    }

    // Metodo para recargar la configuración
    public void reloadPluginConfig(CommandSender sender) {
        reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "¡Configuración recargada!");
    }

    // Executor para el comando /shoting
    public static class ShootingCommandExecutor implements CommandExecutor {

        private final ShootingStarPlugin plugin;

        public ShootingCommandExecutor(ShootingStarPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            // Los comandos requieren el permiso "shotingstar.admin"
            // Se permite que reload y list sean ejecutados desde consola
            boolean isConsole = !(sender instanceof Player);
            if (!isConsole && !sender.hasPermission("shotingstar.admin")) {
                sender.sendMessage(ChatColor.RED + "No tienes permiso para ejecutar este comando.");
                return true;
            }

            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                sender.sendMessage(ChatColor.YELLOW + "Uso de /shoting:");
                sender.sendMessage(ChatColor.GRAY + "/shoting reload - Recarga la configuración (consola o jugador)");
                sender.sendMessage(ChatColor.GRAY + "/shoting shoot <nombre> - Lanza forzosamente una estrella (solo jugador)");
                sender.sendMessage(ChatColor.GRAY + "/shoting list - Lista las estrellas configuradas (consola o jugador)");
                return true;
            }
            if (args[0].equalsIgnoreCase("reload")) {
                plugin.reloadPluginConfig(sender);
                return true;
            }
            if (args[0].equalsIgnoreCase("shoot")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("El comando /shoting shoot solo puede ser ejecutado por un jugador.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Debes especificar el nombre de la estrella.");
                    return true;
                }
                String key = args[1];
                ((Player) sender).sendMessage(ChatColor.YELLOW + "Intentando lanzar la estrella " + key + "...");
                plugin.forceSpawnStar((Player) sender, key);
                return true;
            }
            if (args[0].equalsIgnoreCase("list")) {
                ConfigurationSection section = plugin.getConfig().getConfigurationSection("");
                if (section == null || section.getKeys(false).isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "No hay estrellas configuradas.");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Estrellas configuradas:");
                    for (String key : section.getKeys(false)) {
                        sender.sendMessage(ChatColor.GRAY + "- " + key);
                    }
                }
                return true;
            }
            sender.sendMessage(ChatColor.RED + "Comando desconocido. Usa /shoting help para ver la ayuda.");
            return true;
        }
    }
}
