package org.betterbox.betterGen;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.EventHandler;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.bukkit.Bukkit.getEntity;

public final class BetterGen extends JavaPlugin implements Listener {

    private File generatorsFile;
    private BukkitTask generatorsTaks;
    private Map<UUID,String > spawnedItems = new HashMap<>(); // Map to store references to spawned items
    private Map<String,List<UUID> > stackedItems = new HashMap<>(); // Map to store references to stacked items
    public Map<String, Long> generatorLastSpawnedTimes = new HashMap<>();
    private PluginLogger pluginLogger;
    private String folderPath;
    YamlConfiguration generatorsConfig;
    private Map<String, Generator> generatorsData = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        java.util.logging.Logger logger = this.getLogger();
        folderPath = getDataFolder().getAbsolutePath();
        logger.info("[BetterGen] Initializing");
        logger.info("[BetterGen] Author " + this.getDescription().getAuthors());
        logger.info("[BetterGen] Version  " + this.getDescription().getVersion());
        logger.info("[BetterGen] " + this.getDescription().getDescription());
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        Set<PluginLogger.LogLevel> defaultLogLevels = EnumSet.of(PluginLogger.LogLevel.INFO,PluginLogger.LogLevel.DEBUG, PluginLogger.LogLevel.WARNING, PluginLogger.LogLevel.ERROR);
        pluginLogger = new PluginLogger(folderPath, defaultLogLevels,this);
        pluginLogger.log(PluginLogger.LogLevel.INFO, "Starting startGeneratorsScheduler");
        startGeneratorsScheduler();
        pluginLogger.log(PluginLogger.LogLevel.INFO, "startGeneratorsScheduler started, loading loadGenerators();");



        // Plugin startup logic
        loadGenerators();
        getCommand("bg").setExecutor(this);
        pluginLogger.log(PluginLogger.LogLevel.INFO, "Plugin enabled");
        logger.info("[BetterGen] Running");


    }
    static class Generator {
        String generatorName,itemName, location;
        int itemsPerSpawn, maxItems,spawnedItemsCount, spawnCooldown;


        Generator(String generatorName, String itemName, String location, int itemsPerSpawn, int maxItems, int spawnCooldown) {
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
        public int getCooldown() {
            return this.spawnCooldown;
        }
    }
    public void loadGenerators(){

        generatorsFile = new File(folderPath,"generators.yml");

        if(!generatorsFile.exists()){
            try {
                generatorsFile.createNewFile();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }else{

            generatorsConfig = YamlConfiguration.loadConfiguration(generatorsFile);
            ConfigurationSection generatorsSection = generatorsConfig.getConfigurationSection("generators");
            if(generatorsSection==null){
                return;
            }
            for (String key : generatorsSection.getKeys(false)) {
                ConfigurationSection spawnerSection = generatorsSection.getConfigurationSection(key);
                if (spawnerSection != null) {
                    String location = spawnerSection.getString("location");
                    int cooldown = spawnerSection.getInt("cooldown");
                    int maxItems = spawnerSection.getInt("maxItems");
                    int itemsPerSpawn = spawnerSection.getInt("itemsPerSpawn");
                    String itemName=spawnerSection.getString("itemName");

                    // Zapisywanie danych spawnera do struktury w pamięci
                    generatorsData.put(key, new Generator(key,itemName,location,itemsPerSpawn,maxItems,cooldown));

                }
            }

        }
    }
    @EventHandler
    public void onItemMerge(ItemMergeEvent event) {
        Item source = event.getEntity();
        Item target = event.getTarget();

        pluginLogger.log(PluginLogger.LogLevel.DEBUG,"Item stack merge detected: " + source.getItemStack().getType() + " into " + target.getItemStack().getType());

        // Pobierz nazwę generatora dla przedmiotu źródłowego, jeśli istnieje
        String generatorName = spawnedItems.get(source.getUniqueId());
        if (generatorName != null) {
            // Sprawdzamy, czy dla tego generatora już istnieje lista UUID w mapie stackedItems
            List<UUID> uuidList = stackedItems.computeIfAbsent(generatorName, k -> new ArrayList<>());

            /*
            // Dodajemy UUID przedmiotu źródłowego, jeśli go jeszcze nie ma
            if (!uuidList.contains(source.getUniqueId())) {
                uuidList.add(source.getUniqueId());
            }

             */

            // Dodajemy również UUID przedmiotu docelowego, jeśli go jeszcze nie ma
            if (!uuidList.contains(target.getUniqueId())) {
                uuidList.add(target.getUniqueId());
            }

            // Informacje debugowe
            pluginLogger.log(PluginLogger.LogLevel.DEBUG,"Updated stacked items for generator: " + generatorName);
        } else {
            pluginLogger.log(PluginLogger.LogLevel.DEBUG,"Source item does not belong to any tracked generator.");
        }
    }

    public void checkAndUpdateSpawnedItems() {
        for (Map.Entry<String, List<UUID>> entry : stackedItems.entrySet()) {
            String generatorName = entry.getKey();
            List<UUID> uuidList = entry.getValue();
            Generator generator = generatorsData.get(generatorName);
            int actualCount = 0;

            if (generator == null) {
                continue;  // Jeśli generator nie istnieje, kontynuuj z następnym
            }

            Iterator<UUID> uuidIterator = uuidList.iterator();
            while (uuidIterator.hasNext()) {
                UUID itemId = uuidIterator.next();
                Entity item = Bukkit.getEntity(itemId);

                if (item instanceof Item && item.isValid() && !item.isDead()) {
                    actualCount += ((Item) item).getItemStack().getAmount();  // Sumowanie ilości itemów w stacku
                } else {
                    uuidIterator.remove();  // Usuwanie UUID z listy, jeśli przedmiot jest nieważny
                    pluginLogger.log(PluginLogger.LogLevel.DEBUG, "Removing invalid or dead item with UUID: " + itemId);
                }
            }

            // Ustawianie aktualnej ilości przedmiotów na podstawie obliczonej wartości
            if (generator.spawnedItemsCount != actualCount) {
                generator.spawnedItemsCount = actualCount;
                pluginLogger.log(PluginLogger.LogLevel.DEBUG, "Updated spawnedItemsCount for generator: " + generatorName + " to " + actualCount);
                pluginLogger.log(PluginLogger.LogLevel.DEBUG,"Updated spawnedItemsCount for generator: " + generatorName + " to " + actualCount);
            }
        }
    }

    public void startGeneratorsScheduler() {
        generatorsTaks = new BukkitRunnable() {
            @Override
            public void run() {
                checkAndUpdateSpawnedItems();
                spawnItemFromGenerator();
            }
        }.runTaskTimer(this, 0, 10); // Interval converted to ticks (1 second)
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length==2&&args[1].equals("reload")){
            if(!sender.isOp()){
                    sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[BetterGen]" + ChatColor.DARK_RED + " You don't have permission!");
            }
            else {
                loadGenerators();
                sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[BetterGen]" + ChatColor.AQUA + " Generators reloaded!");
            }
        }
        if(args.length==5) {
            if (sender instanceof Player) {
                if (sender.isOp()) {
                    handleAddSpawnerCommand(sender, args[0], args[1], Integer.parseInt(args[2]),Integer.parseInt(args[3]),Double.parseDouble(args[4]));
                    sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[BetterGen]" + ChatColor.AQUA + " Generator created!");
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
        long currentTime = System.currentTimeMillis();
        long lastUsage = generatorLastSpawnedTimes.getOrDefault(generatorName, 0L);
        boolean canSpawn = (currentTime - lastUsage) >= (cooldown * 1000d);

        pluginLogger.log(PluginLogger.LogLevel.SPAWNERS, "BetterGen.canSpawnItems generatorName: " + generatorName + ", lastUsage: " + lastUsage + ", currentTime: " + currentTime + ", cooldown: " + cooldown + ", canSpawn: " + canSpawn);

        return canSpawn;
    }

    public void spawnItemFromGenerator() {
        long currentTime = System.currentTimeMillis();
            for (Map.Entry<String, Generator> entry : generatorsData.entrySet()) {
                pluginLogger.log(PluginLogger.LogLevel.DEBUG, "Checking generator: " + entry.getKey());
                Generator generator = entry.getValue();
                long lastSpawnTime = generatorLastSpawnedTimes.getOrDefault(entry.getKey(), 0L);

                long timeSinceLastSpawn = currentTime - lastSpawnTime;
                pluginLogger.log(PluginLogger.LogLevel.DEBUG, "Generator " + entry.getKey() + ", lastSpawnTime: "+lastSpawnTime+", currenttime: "+currentTime+", timeSinceLastSpawn: "+timeSinceLastSpawn);
                if (timeSinceLastSpawn < generator.getCooldown()) {
                    pluginLogger.log(PluginLogger.LogLevel.DEBUG, "Generator " + entry.getKey() + " is on cooldown.");
                    continue; // Pomiń ten generator, ponieważ jest na cooldownie
                }

            Location location = getLocationFromString(generator.location);
            if (generator.spawnedItemsCount < generator.maxItems) {
                int toSpawn = Math.min(generator.itemsPerSpawn, generator.maxItems - generator.spawnedItemsCount);
                for (int i = 0; i < toSpawn; i++) {

                    spawnItemAtLocation(location, getItemStackFromString(generator.itemName), entry.getKey());
                }
                generator.spawnedItemsCount += toSpawn;
                generatorLastSpawnedTimes.put(entry.getKey(), System.currentTimeMillis());
            }
                pluginLogger.log(PluginLogger.LogLevel.DEBUG, "Finished checking generator: " + entry.getKey());
        }
    }




    public void spawnItemAtLocation(Location location, ItemStack itemStack, String generatorName) {
        World world = location.getWorld();
        if (world != null && itemStack != null) {
            Item droppedItem = world.dropItemNaturally(location, itemStack);
            spawnedItems.put(droppedItem.getUniqueId(), generatorName);


            Generator generator = generatorsData.get(generatorName);
            if (generator != null) {
                generator.spawnedItemsCount++;
                pluginLogger.log(PluginLogger.LogLevel.DEBUG,"Item spawned from generator" +generatorName+", spawnedItemCount: "+generator.spawnedItemsCount);

            }
        } else {
            pluginLogger.log(PluginLogger.LogLevel.DEBUG,"Invalid world or itemStack for location: " + location.toString());
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
