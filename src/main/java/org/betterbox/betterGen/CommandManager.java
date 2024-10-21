package org.betterbox.betterGen;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class CommandManager implements CommandExecutor {
    private final JavaPlugin plugin;
    private final BetterGen betterGen;
    private final FileManager fileManager;
    private final ConfigManager configManager;
    private PluginLogger pluginLogger;
    public CommandManager(JavaPlugin plugin, BetterGen betterGen, FileManager fileManager, PluginLogger pluginLogger,ConfigManager configManager){
        this.configManager=configManager;
        this.plugin = plugin;
        this.pluginLogger = pluginLogger;
        this.betterGen = betterGen;
        this.fileManager = fileManager;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        pluginLogger.log(PluginLogger.LogLevel.DEBUG, "CommandManager.onCommand called, sender: "+sender+", args: "+args.toString());
        if (args.length==1&&args[0].equals("reload")){
            pluginLogger.log(PluginLogger.LogLevel.DEBUG, "CommandManager.onCommand called,reload, sender: "+sender+", args: "+args.toString());
            if(!sender.isOp()){
                sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[BetterGen]" + ChatColor.DARK_RED + " You don't have permission!");
            }
            else {
                configManager.ReloadConfig();
                betterGen.loadGenerators();
                sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[BetterGen]" + ChatColor.AQUA + " Generators reloaded!");
            }
        }
        if(args.length==6 && args[0].equalsIgnoreCase("create")) {
            pluginLogger.log(PluginLogger.LogLevel.DEBUG, "CommandManager.onCommand args.length==5 create, sender: "+sender+", args: "+args.toString());
            if (sender instanceof Player) {
                if (sender.isOp()) {
                    handleAddSpawnerCommand(sender, args[1], args[2], Integer.parseInt(args[3]),Integer.parseInt(args[4]),Double.parseDouble(args[5]));
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
                targetLocation.add(0, 1, 0);
                betterGen.saveGenerator(targetLocation,generatorName,itemName,itemsPerSpawn,maxItems, Cooldown);
            } else {
                player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[BetterGen]" + ChatColor.DARK_RED + " You don't have permission!");
            }
        }
    }
}
