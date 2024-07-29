package org.betterbox.betterGen;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class BetterGen extends JavaPlugin {

    private File generatorsFile;
    private BukkitTask generatorsTaks;
    public Map<String, Long> generatorLastSpawnedTimes = new HashMap<>();
    private PluginLogger pluginLogger;
    private String folderPath;
    YamlConfiguration generatorsConfig;
    private Map<String, Generator> generatorsData = new HashMap<>();

    @Override
    public void onEnable() {
        Set<PluginLogger.LogLevel> defaultLogLevels = EnumSet.of(PluginLogger.LogLevel.INFO,PluginLogger.LogLevel.DEBUG, PluginLogger.LogLevel.WARNING, PluginLogger.LogLevel.ERROR);
        pluginLogger = new PluginLogger(folderPath, defaultLogLevels,this);
        pluginLogger.log(PluginLogger.LogLevel.INFO, "Starting startGeneratorsScheduler");
        startGeneratorsScheduler();
        pluginLogger.log(PluginLogger.LogLevel.INFO, "startGeneratorsScheduler started, loading loadGenerators();");

        // Plugin startup logic
        loadGenerators();
        pluginLogger.log(PluginLogger.LogLevel.INFO, "Plugin enabled");


    }
    static class Generator {
        String generatorName,itemName, location;
        int itemsPerSpawn, maxItems,spawnedItemsCount;
        double spawnCooldown;


        Generator(String generatorName, String itemName, String location, int itemsPerSpawn, int maxItems, double spawnCooldown) {
            this.generatorName = generatorName;
            this.itemName = itemName;
            this.location = location;
            this.itemsPerSpawn = itemsPerSpawn;
            this.maxItems = maxItems;
            this.spawnCooldown = spawnCooldown;
            this.spawnedItemsCount = 0; // Initialize the spawned mob counter to 0
        }
        public int getSpawnedItemsCount() {
            return this.spawnedItemsCount;
        }
        public int getMaxItems() {
            return this.maxItems;
        }
        public double getCooldown() {
            return this.spawnCooldown;
        }
    }
    public void loadGenerators(){
        folderPath = getDataFolder().getAbsolutePath();
        generatorsFile = new File(folderPath,"generators.yml");
        if(!generatorsFile.exists()){
            try {
                generatorsFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }else{
            generatorsConfig = YamlConfiguration.loadConfiguration(generatorsFile);
            ConfigurationSection generatorsSection = generatorsConfig.getConfigurationSection("generators");
            for (String key : generatorsSection.getKeys(false)) {
                ConfigurationSection spawnerSection = generatorsSection.getConfigurationSection(key);
                if (spawnerSection != null) {
                    String location = spawnerSection.getString("location");
                    double cooldown = spawnerSection.getDouble("cooldown");
                    int maxItems = spawnerSection.getInt("maxItems");
                    int itemsPerSpawn = spawnerSection.getInt("itemsPerSpawn");
                    String itemName=spawnerSection.getString("itemName");

                    String passengerMobName = spawnerSection.getString("passengerMobName", null);

                    // Zapisywanie danych spawnera do struktury w pamięci
                    generatorsData.put(key, new Generator(key,itemName,location,itemsPerSpawn,maxItems,cooldown));

                }
            }

        }
    }
    public void startGeneratorsScheduler() {
        generatorsTaks = new BukkitRunnable() {
            @Override
            public void run() {
                spawnItemFromGenerator();
            }
        }.runTaskTimer(this, 0, 300); // Interval converted to ticks (1 second)
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(args.length==5) {
            if (sender instanceof Player) {
                if (sender.isOp()) {
                    handleAddSpawnerCommand(sender, args[1], args[2], Integer.parseInt(args[3]),Integer.parseInt(args[4]),Double.parseDouble(args[5]));
                    return true;
                }
            }
        }else{
            sender.sendMessage("correct usage /bg genName itemName itemsPerSpawn maxItems Cooldown");
            return false;
        }
        return false;
    }
    public void handleAddSpawnerCommand(CommandSender sender, String generatorName, String itemName, int itemsPerSpawn,int maxItems, double Cooldown) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (player.isOp()) {
                Location targetLocation = player.getTargetBlock(null, 100).getLocation();
                saveGenerator(targetLocation,generatorName,itemName,itemsPerSpawn,maxItems, Cooldown);
            } else {
                player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[BetterGen]" + ChatColor.DARK_RED + " You don't have permission!");
            }
        }
    }
    public void saveGenerator(Location location, String generatorName, String itemName, int itemsPerSpawn, int maxItems, double Cooldown) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(generatorsFile);

        // Zapisywanie danych spawnera
        String path = "generators." + generatorName;
        config.set(path + ".location", location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
        config.set(path + ".itemName", itemName);
        config.set(path + ".itemsPerSpawn", itemsPerSpawn);
        config.set(path + ".maxItems", maxItems);
        config.set(path + ".cooldown", Cooldown);
        try {
            config.save(generatorsFile);
            pluginLogger.log(PluginLogger.LogLevel.INFO, "Generator " + generatorName + " saved to file.");
        } catch (Exception e) {
            pluginLogger.log(PluginLogger.LogLevel.ERROR, "Could not save generator " + generatorName + " to file: " + e.getMessage());
        }

    }
    public Location getLocationFromString(String locationString) {
        try {
            String[] parts = locationString.split(",");
            if (parts.length == 4) {
                World world = Bukkit.getWorld(parts[0]);
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);
                double z = Double.parseDouble(parts[3]);
                //pluginLogger.log(PluginLogger.LogLevel.SPAWNERS, "CustomMobs.getLocationFromString locationString: "+locationString+", ");
                return new Location(world, x, y, z);
            } else {
                return null;
            }
        } catch (Exception e) {
            pluginLogger.log(PluginLogger.LogLevel.ERROR, "Error parsing location string: " + e.getMessage());
            return null;
        }
    }
    private boolean canSpawnItems(String generatorName, double cooldown) {
        pluginLogger.log(PluginLogger.LogLevel.SPAWNERS, "BetterGen.canSpawnItems " + generatorName + " cooldown: "+cooldown);
        if (!generatorLastSpawnedTimes.containsKey(generatorName)) {
            pluginLogger.log(PluginLogger.LogLevel.SPAWNERS, "BetterGen.canSpawnItems check passed, spawner not on the list, return true");
            return true;
        }
        long lastUsage = generatorLastSpawnedTimes.get(generatorName);
        long currentTime = System.currentTimeMillis();
        pluginLogger.log(PluginLogger.LogLevel.SPAWNERS, "BetterGen.canSpawnItems generatorName: "+generatorName+", lastUsage: "+lastUsage+", currentTime: "+currentTime+", timeleft: "+(cooldown-((currentTime-lastUsage)/1000)));
        return (currentTime - lastUsage) >= (cooldown);
    }
    public void spawnItemFromGenerator() {
        //Map<String, Generator> spawnersData = fileManager.spawnersData;
        pluginLogger.log(PluginLogger.LogLevel.DEBUG, "BetterGen.spawnItemFromGenerator called. Loaded generators: " + generatorsData);
        // Sprawdzenie, czy istnieją spawnerzy w pliku
        if (generatorsData.isEmpty()) {
            pluginLogger.log(PluginLogger.LogLevel.ERROR, "No spawners found in generators.yml.");
            return;
        }
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<String, Generator> entry : generatorsData.entrySet()) {
            pluginLogger.log(PluginLogger.LogLevel.DEBUG, "BetterGen.spawnZombieFromSpawner checking spawner: " + entry);
            String generatorName = entry.getKey();
            Generator generator = entry.getValue();
            Location location = getLocationFromString(generator.location);

            while (location.getBlock().getType() != Material.AIR) {
                location.add(0, 1, 0); // Zwiększ y o 1
                if (location.getBlockY() > location.getWorld().getMaxHeight()) {
                    // Jeśli przekraczamy maksymalną wysokość, przerwij pętlę, aby uniknąć pętli nieskończonej
                    System.out.println("Reached the top of the world without finding an AIR block.");
                    break;
                }
            }
            //location.add(0, 1, 0);

            // Sprawdzenie cooldownu
            if (!canSpawnItems(generatorName, generator.getCooldown())) {
                pluginLogger.log(PluginLogger.LogLevel.DEBUG, "generator " + generatorName + " is on cooldown. Current spawnedItemCount: " + generator.spawnedItemsCount);
                continue; // Skip spawning if on cooldown
            }

            // Spawnowanie zombiaków na podanej lokalizacji
            if (location != null) {
                World world = location.getWorld();
                if (world != null) {
                    ItemStack itemToSpawn = getItemStackFromString(generator.itemName);
                    int spawnedItemsCount = generator.spawnedItemsCount;
                    // Get the remaining slots for spawning mobs
                    int maxItems = generator.maxItems;
                    int itemsPerSpawn=generator.itemsPerSpawn;
                    int remainingSlots = Math.max(0, maxItems - spawnedItemsCount);
                    //pluginLogger.log(PluginLogger.LogLevel.SPAWNERS, "CustomMobs.spawnZombieFromSpawner "+spawnerName+", maxMobs: "+maxMobs+", remaining slots: "+remainingSlots);
                    if (remainingSlots == 0) {
                        pluginLogger.log(PluginLogger.LogLevel.DEBUG, "BetterGen.spawnItemFromGenerator 0 remaining slots for " + generatorName);
                        continue;
                    }

                    int mobsToSpawn = Math.min(itemsPerSpawn, remainingSlots);
                    int spawnedItems = 0;
                    pluginLogger.log(PluginLogger.LogLevel.DEBUG, "BetterGen.spawnItemFromGenerator " + generatorName + ", maxItems: " + maxItems +",itemToSpawn: "+itemToSpawn+", remaining slots: " + remainingSlots + ", itemsPerSpawn: " + itemsPerSpawn + ", spawnerData.spawnedMobCount: " + spawnedItemsCount);
                    for (int i = 0; i < mobsToSpawn; i++) {
                        spawnItemAtLocation(location,itemToSpawn);
                        generator.spawnedItemsCount++;
                        spawnedItems++;
                    }
                    pluginLogger.log(PluginLogger.LogLevel.DEBUG, "BetterGen.spawnItemFromGenerator spawnedItems: " + spawnedItems);
                    // Ustawianie czasu ostatniego respa mobów z tego spawnera
                    generatorLastSpawnedTimes.put(generatorName, currentTime);
                } else {
                    pluginLogger.log(PluginLogger.LogLevel.ERROR, "Invalid world specified for generator " + generatorName);
                }
            } else {
                pluginLogger.log(PluginLogger.LogLevel.ERROR, "Invalid location specified for generator " + generatorName);
            }
        }
    }
    public void spawnItemAtLocation(Location location, ItemStack itemStack) {
        World world = location.getWorld();
        if (world != null) {
            Item droppedItem = world.dropItemNaturally(location, itemStack);
            pluginLogger.log(PluginLogger.LogLevel.DEBUG, "spawnItemAtLocation: Spawned item " + itemStack.getType() + " at location " + location.toString());
        } else {
            pluginLogger.log(PluginLogger.LogLevel.ERROR, "Invalid world for location: " + location.toString());
        }
    }
    public ItemStack getItemStackFromString(String itemName) {
        Material material = Material.getMaterial(itemName.toUpperCase());
        if (material != null) {
            return new ItemStack(material);
        } else {
            pluginLogger.log(PluginLogger.LogLevel.ERROR, "Invalid item name: " + itemName);
            return null;
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
