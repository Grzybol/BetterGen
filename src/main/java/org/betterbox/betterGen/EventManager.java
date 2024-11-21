package org.betterbox.betterGen;

import org.betterbox.elasticBuffer.ElasticBuffer;
import org.betterbox.elasticBuffer.ElasticBufferAPI;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EventManager implements Listener {
    private final BetterGen betterGen;
    private final PluginLogger pluginLogger;
    private final Plugin plugin;
    ElasticBuffer elasticBuffer;
    public EventManager(PluginLogger pluginLogger, BetterGen betterGen,ElasticBuffer elasticBuffer, Plugin plugin){
        this.pluginLogger=pluginLogger;
        this.plugin=plugin;
        this.betterGen=betterGen;
        this.elasticBuffer=elasticBuffer;
    }
    @EventHandler
    public void onItemMerge(ItemMergeEvent event) {
        String transactionID = UUID.randomUUID().toString();
        Item mergingItem = event.getEntity(); // Przedmiot, który jest łączony
        if (mergingItem.hasMetadata("handledMerge")) {
            pluginLogger.log(PluginLogger.LogLevel.DEBUG,"Event.onItemMerge event already handled! Item: " + mergingItem.getItemStack().getType(),transactionID);
            return;
        }
        mergingItem.setMetadata("handledMerge", new FixedMetadataValue(plugin, true));

        // Logowanie dla potwierdzenia, że event został obsłużony
        pluginLogger.log(PluginLogger.LogLevel.DEBUG, "Handled ItemMerge event for Item: " + mergingItem.getItemStack().getType());


        Item source = event.getEntity();
        Item target = event.getTarget();

         pluginLogger.log(PluginLogger.LogLevel.DEBUG,"Item stack merge detected: " + source.getItemStack().getType() + " into " + target.getItemStack().getType(),transactionID);

        // Pobierz nazwę generatora dla przedmiotu źródłowego, jeśli istnieje
        String generatorName = betterGen.spawnedItems.get(source.getUniqueId());

        if (generatorName != null) {
            pluginLogger.log(PluginLogger.LogLevel.DEBUG,"onItemMerge generatorName: " + generatorName+", target.getUniqueId(): "+target.getUniqueId(),transactionID);
            // Sprawdzamy, czy dla tego generatora już istnieje lista UUID w mapie stackedItems
            List<UUID> uuidList = betterGen.stackedItems.computeIfAbsent(generatorName, k -> new ArrayList<>());
            // Dodajemy również UUID przedmiotu docelowego, jeśli go jeszcze nie ma
            if (!uuidList.contains(target.getUniqueId())) {

                uuidList.add(target.getUniqueId());
                pluginLogger.log(PluginLogger.LogLevel.DEBUG,"onItemMerge generatorName: " + generatorName+", target.getUniqueId(): "+target.getUniqueId()+" added, uuidList: "+uuidList.toString(),transactionID);
            }
            pluginLogger.log(PluginLogger.LogLevel.DEBUG,"Updated stacked items for generator: " + generatorName,transactionID);
            // Informacje debugowe
        } else {
            pluginLogger.log(PluginLogger.LogLevel.DEBUG,"Source item does not belong to any tracked generator.");
        }
    }
}
