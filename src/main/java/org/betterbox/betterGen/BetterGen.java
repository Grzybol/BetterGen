package org.betterbox.betterGen;

import org.betterbox.elasticBuffer.ElasticBuffer;
import org.betterbox.elasticBuffer.ElasticBufferAPI;
import org.betterbox.elasticBuffer.ElasticBufferPluginLogger;
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
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.EventHandler;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.Buffer;
import java.nio.charset.StandardCharsets;
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
    public ElasticBuffer elasticBuffer;
    private Map<String, Generator> generatorsData = new HashMap<>();
    FileManager fileManager;
    ElasticBufferAPI api;
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
        try{
            PluginManager pm = Bukkit.getPluginManager();
            Plugin[] plugins = pm.getPlugins();
            StringBuilder enabledPlugins = new StringBuilder("Enabled plugins: ");
            StringBuilder disabledPlugins = new StringBuilder("Disabled plugins: ");
            for (Plugin plugin : plugins) {
                if (plugin.isEnabled()) {
                    enabledPlugins.append(plugin.getName()).append(", ");
                } else {
                    disabledPlugins.append(plugin.getName()).append(", ");
                }
            }
        // Zalogowanie włączonych pluginów
            pluginLogger.log(PluginLogger.LogLevel.INFO, enabledPlugins.toString());
        // Zalogowanie wyłączonych pluginów
            pluginLogger.log(PluginLogger.LogLevel.INFO,  "Bukkit.getPluginManager().getPlugin(\"ElasticBuffer\").isEnabled():"+Bukkit.getPluginManager().getPlugin("ElasticBuffer").isEnabled()+",Bukkit.getPluginManager().getPlugin(\"ElasticBuffer\").isNaggable(): "+Bukkit.getPluginManager().getPlugin("ElasticBuffer").isNaggable());
            pluginLogger.log(PluginLogger.LogLevel.INFO, disabledPlugins.toString());
            pluginLogger.log(PluginLogger.LogLevel.INFO, "[BetterGen] Initializing basic components...");
            try {
                // Opóźnienie o 5 sekund, aby dać ElasticBuffer czas na pełną inicjalizację
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                pluginLogger.log(PluginLogger.LogLevel.WARNING, "[BetterGen] Initialization delay interrupted: " + e.getMessage());
                Thread.currentThread().interrupt(); // Przywrócenie statusu przerwania wątku
            }
            //WORKING VERSION KURWA FINALLY
            elasticBuffer = (ElasticBuffer) pm.getPlugin("ElasticBuffer");
            pluginLogger.log(PluginLogger.LogLevel.DEBUG, "elasticBuffer: " + elasticBuffer);
            assert elasticBuffer != null;
            elasticBuffer.receiveLog("BetterGen initialized successfully! Starting schedulers", "INFO", getDescription().getName(),null);
            elasticBuffer.sendLogs();
            pluginLogger.log(PluginLogger.LogLevel.DEBUG, "LOGS SENT");
            //WORKING VERSION KURWA FINALLY
            startCheckAndUpdateTask();
            startGeneratorsScheduler();


            // Sprawdzenie, czy ElasticBuffer jest załadowany
            /*
            ElasticBufferAPI api = Bukkit.getServicesManager().load(ElasticBufferAPI.class);
            if (api != null) {
                pluginLogger.log(PluginLogger.LogLevel.INFO, "[BetterGen] Successfully loaded ElasticBufferAPI.");
                elasticBuffer.receiveLog("Successfully loaded ElasticBufferAPI.", "INFO", getDescription().getName());
            } else {
                pluginLogger.log(PluginLogger.LogLevel.WARNING, "[BetterGen] Could not load ElasticBufferAPI. ElasticBuffer may not be enabled yet.");
            }

             */
        }catch (Exception e){
            pluginLogger.log(PluginLogger.LogLevel.ERROR, "ElasticBufferAPI instance found via ServicesManager, exception: "+e.getMessage());
        }
        folderPath =getDataFolder().getAbsolutePath();
        configManager = new ConfigManager(this, pluginLogger, folderPath);
        fileManager = new FileManager(getDataFolder().getAbsolutePath(),this,this,pluginLogger);
        getCommand("bg").setExecutor(new CommandManager(this,this,fileManager,pluginLogger,configManager,elasticBuffer));
        eventManager = new EventManager(pluginLogger,this, elasticBuffer);
        getServer().getPluginManager().registerEvents(eventManager, this);
        pluginLogger.log(PluginLogger.LogLevel.INFO, "Starting startGeneratorsScheduler and loadGenerators()");
        loadGenerators();
        pluginLogger.log(PluginLogger.LogLevel.INFO, "Generators loaded, starting schedulers");
        startCheckAndUpdateTask();
        startGeneratorsScheduler();
        pluginLogger.log(PluginLogger.LogLevel.INFO, "Schedulers started");
        pluginLogger.log(PluginLogger.LogLevel.INFO, "Plugin enabled");
        logger.info("[BetterGen] Running");
        //sendLogToElasticsearch("BetterGen plugin started", "INFO");
        pluginLogger.log(PluginLogger.LogLevel.INFO, "Testing ElasticBuffer connection");



    }


    private void completeInitialization() {
        // Tutaj wykonujemy wszystkie kluczowe operacje, które zależą od ElasticBuffer
        Plugin plugin = Bukkit.getPluginManager().getPlugin("ElasticBuffer");
        if (plugin instanceof ElasticBuffer) {
            ElasticBuffer bufferPlugin = (ElasticBuffer) plugin;
            api = Bukkit.getServicesManager().load(ElasticBufferAPI.class);

            if (api != null) {
                pluginLogger.log(PluginLogger.LogLevel.INFO, "[BetterGen] Successfully loaded ElasticBufferAPI.");
                elasticBuffer.receiveLog("BetterGen initialized successfully!", "INFO", getDescription().getName(),null);
            } else {
                pluginLogger.log(PluginLogger.LogLevel.ERROR, "[BetterGen] Failed to load ElasticBufferAPI.");
            }

            // Pozostała inicjalizacja pluginu
            pluginLogger.log(PluginLogger.LogLevel.INFO, "[BetterGen] Proceeding with further initialization...");
            // Tutaj można umieścić kod odpowiedzialny za eventy, commandy, generatory itd.
            startCheckAndUpdateTask();
            startGeneratorsScheduler();
        } else {
            pluginLogger.log(PluginLogger.LogLevel.ERROR, "[BetterGen] ElasticBuffer plugin not available, initialization aborted.");
        }
    }

    public void startCheckAndUpdateTask() {
        String transactionID = UUID.randomUUID().toString();
        elasticBuffer.receiveLog("Starting schedulers", "INFO","BetterGen",transactionID);
        // Uruchamianie asynchronicznie co 0.5 sekundy
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            elasticBuffer.receiveLog("calling checkAndUpdateSpawnedItems()", "DEBUG","BetterGen",transactionID);
            // Przełącz na główny wątek serwera do interakcji z encjami
            Bukkit.getScheduler().runTask(this, () -> {
                checkAndUpdateSpawnedItems();
                elasticBuffer.receiveLog("checkAndUpdateSpawnedItems() finished", "DEBUG","BetterGen",transactionID);
            });
        }, 0L, 5L);  // 0.5 sekundy w tickach (10 ticków)
    }
    public void cancelGeneratorsTasks() {
        elasticBuffer.receiveLog("cancelGeneratorsTasks() called", "DEBUG","BetterGen",null);
        if (generatorsTaks != null) {
            generatorsTaks.cancel();  // Anuluj bieżące zadanie, jeśli istnieje
            elasticBuffer.receiveLog("cancelGeneratorsTasks() generatorsTaks"+generatorsTaks+" cancelled", "DEBUG","BetterGen",null);
        }
    }
    public void startGeneratorsScheduler() {
        String transactionID = UUID.randomUUID().toString();
        elasticBuffer.receiveLog("startGeneratorsScheduler() called", "DEBUG","BetterGen",transactionID);
        cancelGeneratorsTasks();
        pluginLogger.log(PluginLogger.LogLevel.DEBUG, "BetterGen.startGeneratorsScheduler called" );
        for (Map.Entry<String, Generator> entry : generatorsData.entrySet()) {
            Generator generator = entry.getValue();
            pluginLogger.log(PluginLogger.LogLevel.DEBUG, "BetterGen.startGeneratorsScheduler starting scheduler for "+generator.generatorName );
            elasticBuffer.receiveLog("BetterGen.startGeneratorsScheduler starting scheduler for "+generator.generatorName, "DEBUG","BetterGen",transactionID);
            long cooldownTicks = generator.getCooldown() / 50;  // Przeliczanie milisekund na ticki
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                pluginLogger.log(PluginLogger.LogLevel.DEBUG, "BetterGen.startGeneratorsScheduler scheduler started for "+generator.generatorName );
                elasticBuffer.receiveLog("BetterGen.startGeneratorsScheduler scheduler started for "+generator.generatorName, "DEBUG","BetterGen",transactionID);
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
        String transactionID = UUID.randomUUID().toString();
        elasticBuffer.receiveLog("BetterGen.checkAndUpdateSpawnedItems called","DEBUG","BetterGen",transactionID);

        for (Map.Entry<String, List<UUID>> entry : stackedItems.entrySet()) {
            String generatorName = entry.getKey();
            List<UUID> uuidList = entry.getValue();
            elasticBuffer.receiveLog("BetterGen.checkAndUpdateSpawnedItems checking generator "+generatorName+", uuidList "+uuidList.toString(),"DEBUG","BetterGen",transactionID);


            Generator generator = generatorsData.get(generatorName);
            int actualCount = 0;

            if (generator == null) {
                elasticBuffer.receiveLog("BetterGen.checkAndUpdateSpawnedItems - Generator not found for:" + generatorName,"DEBUG","BetterGen",transactionID);
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
                    pluginLogger.log(PluginLogger.LogLevel.DEBUG_LOCAL, "Removing invalid or dead item from the uuidList with UUID: " + itemId);
                    elasticBuffer.receiveLog("BetterGen.checkAndUpdateSpawnedItems Removing invalid or dead item from the uuidList with UUID: " + itemId,"DEBUG","BetterGen",transactionID);
                }
            }

            // Ustawianie aktualnej ilości przedmiotów na podstawie obliczonej wartości
            if (generator.spawnedItemsCount != actualCount) {
                generator.spawnedItemsCount = actualCount;
                elasticBuffer.receiveLog("BetterGen.checkAndUpdateSpawnedItems Updated spawnedItemsCount for generator: " + generatorName + " to " + actualCount,"DEBUG","BetterGen",transactionID);
                pluginLogger.log(PluginLogger.LogLevel.DEBUG_LOCAL, "Updated spawnedItemsCount for generator: " + generatorName + " to " + actualCount);
            }
        }
    }



    public void saveGenerator(Location location, String generatorName, String itemName, int itemsPerSpawn, int maxItems, double Cooldown) {
        String transactionID = UUID.randomUUID().toString();
        elasticBuffer.receiveLog("BetterGen.saveGenerator called. location:" + location+", generatorName:"+generatorName+", itemName:"+itemName+", itemsPerSpawn:"+itemsPerSpawn+", maxItems:"+maxItems+", Cooldown:"+Cooldown,"DEBUG","BetterGen",transactionID);
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
            elasticBuffer.receiveLog("BetterGen.saveGenerator Generator " + generatorName + " saved to file.","INFO","BetterGen",transactionID);
            pluginLogger.log(PluginLogger.LogLevel.INFO, "Generator " + generatorName + " saved to file.");
        } catch (Exception e) {
            elasticBuffer.receiveLog("BetterGen.saveGenerator Could not save generator " + generatorName + " to file: " + e.getMessage(),"ERROR","BetterGen",transactionID);
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
            elasticBuffer.receiveLog("Error parsing location string: " + e.getMessage(), "ERROR","BetterGen",null);
            return null;
        }
    }
    public void spawnItemFromGeneratorv2(Generator generator) {
        String transactionID = UUID.randomUUID().toString();
        elasticBuffer.receiveLog("BetterGen.spawnItemFromGeneratorv2 called, generator: "+generator, "DEBUG","BetterGen",transactionID);
        pluginLogger.log(PluginLogger.LogLevel.DEBUG_LOCAL, "BetterGen.spawnItemFromGeneratorv2 called, " + generator.generatorName);
        String generatorName = generator.generatorName;
        long lastSpawnTime = generatorLastSpawnedTimes.getOrDefault(generatorName, 0L);
        long currentTime = System.currentTimeMillis();  // Czas w milisekundach
        long timeSinceLastSpawn = currentTime - lastSpawnTime;
        pluginLogger.log(PluginLogger.LogLevel.DEBUG_LOCAL, "BetterGen.spawnItemFromGeneratorv2 generatorName: " + generator.generatorName+", timeSinceLastSpawn: "+timeSinceLastSpawn+", spawnedItemsCount: "+generator.spawnedItemsCount+", maxItems: "+generator.maxItems);
        elasticBuffer.receiveLog("BetterGen.spawnItemFromGeneratorv2 generator:"+generator+", generatorName: " + generator.generatorName+", timeSinceLastSpawn: "+timeSinceLastSpawn+", spawnedItemsCount: "+generator.spawnedItemsCount+", maxItems: "+generator.maxItems,"DEBUG","BetterGen",transactionID);
        if (timeSinceLastSpawn >= generator.getCooldown()) {  // Cooldown jest już w milisekundach
            pluginLogger.log(PluginLogger.LogLevel.DEBUG, "BetterGen.spawnItemFromGeneratorv2 " + generator.generatorName+" not on delay");
            elasticBuffer.receiveLog("BetterGen.spawnItemFromGeneratorv2 generatorName: " + generator.generatorName+", generatorName:" + generator.generatorName+" not on delay","DEBUG","BetterGen",transactionID);
            Location location = getLocationFromString(generator.location);

            if (generator.spawnedItemsCount < generator.maxItems) {
                spawnItems(location, generator);
                generatorLastSpawnedTimes.put(generatorName, currentTime);
            }
        }
    }
    private void spawnItems(Location location, Generator generator) {
        String transactionID = UUID.randomUUID().toString();
        World world = location.getWorld();
        int toSpawn = Math.min(generator.itemsPerSpawn, generator.maxItems - generator.spawnedItemsCount);
        int counter=0;
        elasticBuffer.receiveLog("BetterGen.spawnItems called generator"+generator+", generatorName" + generator.generatorName+", toSpawn: "+toSpawn,"DEBUG","BetterGen",transactionID);
        pluginLogger.log(PluginLogger.LogLevel.DEBUG, "BetterGen.spawnItems called " + generator.generatorName+", toSpawn: "+toSpawn);
        for (int i = 0; i < toSpawn; i++) {
            ItemStack itemToSpawn = getItemStackFromString(generator.itemName);
            Item item = (Item) world.dropItemNaturally(location, itemToSpawn);
            item.setVelocity(new Vector(0, 0, 0));
            spawnedItems.put(item.getUniqueId(), generator.generatorName);
            generator.spawnedItemsCount++;
            counter++;
        }
        elasticBuffer.receiveLog("BetterGen.spawnItems called generator"+generator+", spawned items count: "+counter+", toSpawn: "+toSpawn,"DEBUG","BetterGen",transactionID);
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
