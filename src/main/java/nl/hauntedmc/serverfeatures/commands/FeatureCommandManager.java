package nl.hauntedmc.serverfeatures.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class FeatureCommandManager {

    private final JavaPlugin plugin;
    private final Logger logger;
    private CommandMap commandMap;
    private final Map<String, FeatureCommand> registeredCommands = new HashMap<>();

    public FeatureCommandManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        initializeCommandMap();
    }

    /**
     * Uses reflection to get the Bukkit CommandMap safely.
     */
    private void initializeCommandMap() {
        commandMap = plugin.getServer().getCommandMap();
    }

    /**
     * Registers a command dynamically at runtime.
     */
    public boolean registerCommand(String commandName, CommandExecutor executor) {
        if (commandMap == null) {
            logger.severe("CommandMap is not initialized. Cannot register command: " + commandName);
            return false;
        }

        if (registeredCommands.containsKey(commandName)) {
            logger.warning("Command " + commandName + " is already registered.");
            return false;
        }

        FeatureCommand command = new FeatureCommand(commandName, executor);
        commandMap.register(plugin.getDescription().getName(), command);
        registeredCommands.put(commandName, command);
        logger.warning("Command " + commandName + " is registered.");
        return true;
    }

    /**
     * Unregisters a command dynamically.
     */
    public boolean unregisterCommand(String commandName) {
        if (!registeredCommands.containsKey(commandName)) {
            logger.warning("Command " + commandName + " is not registered.");
            return false;
        }

        Command command = registeredCommands.remove(commandName);
        command.unregister(commandMap);
        commandMap.getKnownCommands().remove(commandName);

        logger.warning("Command " + commandName + " is unregistered.");
        return true;
    }

    /**
     * Unregisters all dynamically registered commands safely.
     */
    public void unregisterAllCommands() {
        // Copy the keys to prevent ConcurrentModificationException
        List<String> commandNames = new ArrayList<>(registeredCommands.keySet());

        for (String commandName : commandNames) {
            unregisterCommand(commandName);
        }
    }

}
