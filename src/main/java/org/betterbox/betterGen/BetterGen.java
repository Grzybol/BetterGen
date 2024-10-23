package org.betterbox.betterGen;

import org.bstats.bukkit.Metrics;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
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
import org.bukkit.util.Vector;

import static org.bukkit.Bukkit.getEntity;

public final class BetterGen extends JavaPlugin implements Listener {

    private File generatorsFile;
    private BukkitTask generatorsTaks;
    Map<UUID,String > spawnedItems = new HashMap<>(); // Map to store references to spawned items

    Map<String,List<UUID> > stackedItems = new HashMap<>(); // Map to store references to stacked items
    public Map<String, Long> generatorLastSpawnedTimes = new HashMap<>();
    private PluginLogger pluginLogger;
    private String folderPath;
    YamlConfiguration generatorsConfig;
    private Map<String, Generator> generatorsData = new HashMap<>();
    FileManager fileManager;
    ConfigManager configManager;
    EventManager eventManager;

    @Override
    public void onEnable() {
        int pluginId = 22834; // Zamień na rzeczywisty ID twojego pluginu na bStats
        Metrics metrics = new Metrics(this, pluginId);
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
        Set<PluginLogger.LogLevel> defaultLogLevels = EnumSet.of(PluginLogger.LogLevel.INFO, PluginLogger.LogLevel.WARNING, PluginLogger.LogLevel.ERROR);
        pluginLogger = new PluginLogger(folderPath, defaultLogLevels,this);
        folderPath =getDataFolder().getAbsolutePath();
        configManager = new ConfigManager(this, pluginLogger, folderPath);
        fileManager = new FileManager(getDataFolder().getAbsolutePath(),this,this,pluginLogger);
        getCommand("bg").setExecutor(new CommandManager(this,this,fileManager,pluginLogger,configManager));
        eventManager = new EventManager(pluginLogger,this);
        getServer().getPluginManager().registerEvents(eventManager, this);
        pluginLogger.log(PluginLogger.LogLevel.INFO, "Starting startGeneratorsScheduler and loadGenerators()");
        loadGenerators();
        pluginLogger.log(PluginLogger.LogLevel.INFO, "Generators loaded, starting schedulers");
        startCheckAndUpdateTask();
        startGeneratorsScheduler();
        pluginLogger.log(PluginLogger.LogLevel.INFO, "Schedulers started");
        pluginLogger.log(PluginLogger.LogLevel.INFO, "Plugin enabled");
        logger.info("[BetterGen] Running");


    }
    public void startCheckAndUpdateTask() {
        // Uruchamianie asynchronicznie co 0.5 sekundy
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            // Przełącz na główny wątek serwera do interakcji z encjami
            Bukkit.getScheduler().runTask(this, () -> {
                checkAndUpdateSpawnedItems();
            });
        }, 0L, 5L);  // 0.5 sekundy w tickach (10 ticków)
    }
    public void cancelGeneratorsTasks() {
        if (generatorsTaks != null) {
            generatorsTaks.cancel();  // Anuluj bieżące zadanie, jeśli istnieje
        }
    }
    public void startGeneratorsScheduler() {
        cancelGeneratorsTasks();
        pluginLogger.log(PluginLogger.LogLevel.DEBUG, "BetterGen.startGeneratorsScheduler called" );
        for (Map.Entry<String, Generator> entry : generatorsData.entrySet()) {
            Generator generator = entry.getValue();
            pluginLogger.log(PluginLogger.LogLevel.DEBUG, "BetterGen.startGeneratorsScheduler starting scheduler for "+generator.generatorName );
            long cooldownTicks = generator.getCooldown() / 50;  // Przeliczanie milisekund na ticki
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                pluginLogger.log(PluginLogger.LogLevel.DEBUG, "BetterGen.startGeneratorsScheduler scheduler started for "+generator.generatorName );
                spawnItemFromGeneratorv2(generator);
            }, 0L, cooldownTicks);
        }
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
        generatorsData.clear();

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
    public void spawnItemFromGeneratorv2(Generator generator) {
        pluginLogger.log(PluginLogger.LogLevel.DEBUG, "BetterGen.spawnItemFromGeneratorv2 called, " + generator.generatorName);
        String generatorName = generator.generatorName;
        long lastSpawnTime = generatorLastSpawnedTimes.getOrDefault(generatorName, 0L);
        long currentTime = System.currentTimeMillis();  // Czas w milisekundach
        long timeSinceLastSpawn = currentTime - lastSpawnTime;
        pluginLogger.log(PluginLogger.LogLevel.DEBUG, "BetterGen.spawnItemFromGeneratorv2 generatorName: " + generator.generatorName+", timeSinceLastSpawn: "+timeSinceLastSpawn+", spawnedItemsCount: "+generator.spawnedItemsCount+", maxItems: "+generator.maxItems);
        if (timeSinceLastSpawn >= generator.getCooldown()) {  // Cooldown jest już w milisekundach
            pluginLogger.log(PluginLogger.LogLevel.DEBUG, "BetterGen.spawnItemFromGeneratorv2 " + generator.generatorName+" not on delay");
            Location location = getLocationFromString(generator.location);

            if (generator.spawnedItemsCount < generator.maxItems) {
                spawnItems(location, generator);
                generatorLastSpawnedTimes.put(generatorName, currentTime);
            }
        }
    }
    private void spawnItems(Location location, Generator generator) {

        World world = location.getWorld();
        int toSpawn = Math.min(generator.itemsPerSpawn, generator.maxItems - generator.spawnedItemsCount);
        int counter=0;
        pluginLogger.log(PluginLogger.LogLevel.DEBUG, "BetterGen.spawnItems called " + generator.generatorName+", toSpawn: "+toSpawn);
        for (int i = 0; i < toSpawn; i++) {
            ItemStack itemToSpawn = getItemStackFromString(generator.itemName);
            Item item = (Item) world.dropItemNaturally(location, itemToSpawn);
            item.setVelocity(new Vector(0, 0, 0));
            spawnedItems.put(item.getUniqueId(), generator.generatorName);
            generator.spawnedItemsCount++;
            counter++;
        }
        pluginLogger.log(PluginLogger.LogLevel.DEBUG, "Items spawned for generator: " + generator.generatorName+", spawned items count: "+counter);
    }
    public void spawnItemFromGenerator() {

            for (Map.Entry<String, Generator> entry : generatorsData.entrySet()) {
                pluginLogger.log(PluginLogger.LogLevel.DEBUG, "Checking generator: " + entry.getKey());
                Generator generator = entry.getValue();
                String generatorName = generator.generatorName;
                long lastSpawnTime = generatorLastSpawnedTimes.getOrDefault(entry.getKey(), 0L);
                long currentTime = System.currentTimeMillis();
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

                    World world = location.getWorld();
                    generator.spawnedItemsCount++;
                    pluginLogger.log(PluginLogger.LogLevel.DEBUG,"Item spawned from generator" +generatorName+", spawnedItemCount: "+generator.spawnedItemsCount);
                    ItemStack itemToSpawn = getItemStackFromString(generator.itemName);
                    Item item = (Item) world.dropItemNaturally(location,itemToSpawn);
                    item.setVelocity(new Vector(0, 0, 0));
                    //addUUIDtoStack(generatorName, item.getUniqueId());
                    spawnedItems.put(item.getUniqueId(), generatorName);

                    //spawnItemAtLocation(location, getItemStackFromString(generator.itemName), entry.getKey());
                }
                generator.spawnedItemsCount += toSpawn;
                generatorLastSpawnedTimes.put(entry.getKey(), System.currentTimeMillis());
            }
                pluginLogger.log(PluginLogger.LogLevel.DEBUG, "Finished checking generator: " + entry.getKey());
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
