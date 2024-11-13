package org.betterbox.betterGen;

import org.betterbox.elasticBuffer.ElasticBuffer;
import org.betterbox.elasticBuffer.ElasticBufferAPI;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemMergeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EventManager implements Listener {
    private final BetterGen betterGen;
    private final PluginLogger pluginLogger;
    ElasticBuffer elasticBuffer;
    public EventManager(PluginLogger pluginLogger, BetterGen betterGen,ElasticBuffer elasticBuffer){
        this.pluginLogger=pluginLogger;
        this.betterGen=betterGen;
        this.elasticBuffer=elasticBuffer;
    }
    @EventHandler
    public void onItemMerge(ItemMergeEvent event) {
        String transactionID = UUID.randomUUID().toString();
        Item source = event.getEntity();
        Item target = event.getTarget();

        pluginLogger.log(PluginLogger.LogLevel.DEBUG,"onItemMerge Item stack merge detected: " + source.getItemStack().getType() + " into " + target.getItemStack().getType());
        elasticBuffer.receiveLog("Item stack merge detected: " + source.getItemStack().getType() + " into " + target.getItemStack().getType(), "DEBUG","BetterGen",transactionID);

        // Pobierz nazwę generatora dla przedmiotu źródłowego, jeśli istnieje
        String generatorName = betterGen.spawnedItems.get(source.getUniqueId());

        if (generatorName != null) {
            elasticBuffer.receiveLog("onItemMerge generatorName: " + generatorName+", target.getUniqueId(): "+target.getUniqueId(), "DEBUG","BetterGen",transactionID);
            // Sprawdzamy, czy dla tego generatora już istnieje lista UUID w mapie stackedItems
            List<UUID> uuidList = betterGen.stackedItems.computeIfAbsent(generatorName, k -> new ArrayList<>());
            // Dodajemy również UUID przedmiotu docelowego, jeśli go jeszcze nie ma
            if (!uuidList.contains(target.getUniqueId())) {

                uuidList.add(target.getUniqueId());
                elasticBuffer.receiveLog("onItemMerge generatorName: " + generatorName+", target.getUniqueId(): "+target.getUniqueId()+" added, uuidList: "+uuidList.toString(), "DEBUG","BetterGen",transactionID);
            }
            elasticBuffer.receiveLog("Updated stacked items for generator: " + generatorName, "DEBUG","BetterGen",transactionID);
            // Informacje debugowe
            pluginLogger.log(PluginLogger.LogLevel.DEBUG,"Updated stacked items for generator: " + generatorName);
        } else {
            pluginLogger.log(PluginLogger.LogLevel.DEBUG,"Source item does not belong to any tracked generator.");
        }
    }
}
