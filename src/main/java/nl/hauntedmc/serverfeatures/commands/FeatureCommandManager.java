package nl.hauntedmc.serverfeatures.commands;

import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class FeatureCommandManager {

    private final JavaPlugin plugin;
    private final CommandMap commandMap;
    private final Map<String, FeatureCommand> registeredCommands = new HashMap<>();

    public FeatureCommandManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.commandMap = plugin.getServer().getCommandMap();
    }

    /**
     * Registers a command dynamically at runtime with an optional tab completer.
     */
    public void registerCommand(String commandName, CommandExecutor executor, TabCompleter tabCompleter) {
        if (commandMap == null) {
            plugin.getLogger().severe("CommandMap is not initialized. Cannot register command: " + commandName);
            return;
        }

        if (registeredCommands.containsKey(commandName)) {
            plugin.getLogger().warning("Command " + commandName + " is already registered.");
            return;
        }

        FeatureCommand command = new FeatureCommand(commandName, executor, tabCompleter);
        commandMap.register(plugin.getDescription().getName(), command);
        registeredCommands.put(commandName, command);

        plugin.getLogger().info("Registered command: " + commandName);
    }

    /**
     * Unregisters a command dynamically.
     */
    public void unregisterCommand(String commandName) {
        if (!registeredCommands.containsKey(commandName)) {
            plugin.getLogger().warning("Command " + commandName + " is not registered.");
            return;
        }

        FeatureCommand command = registeredCommands.remove(commandName);
        command.unregister(commandMap);
        commandMap.getKnownCommands().remove(commandName);

        plugin.getLogger().info("Unregistered command: " + commandName);
    }

    /**
     * Unregisters all dynamically registered commands safely.
     */
    public void unregisterAllCommands() {
        List<String> commandNames = new ArrayList<>(registeredCommands.keySet());
        for (String commandName : commandNames) {
            unregisterCommand(commandName);
        }
    }
}
