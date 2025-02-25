package nl.hauntedmc.serverfeatures.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.commands.FeatureCommand;
import org.bukkit.command.*;

import java.util.*;

public class FeatureCommandManager {

    private final ServerFeatures plugin;
    private final CommandMap commandMap;

    private final Map<String, FeatureCommand> registeredCommands = new HashMap<>();

    public FeatureCommandManager(ServerFeatures plugin) {
        this.plugin = plugin;
        this.commandMap = plugin.getServer().getCommandMap();
    }

    /**
     * Registers a command dynamically at runtime with an optional tab completer.
     */
    public void registerFeatureCommand(FeatureCommand command) {
        String commandName = command.getName();

        if (commandMap == null) {
            plugin.getLogger().severe("CommandMap is not initialized. Cannot register command: " + commandName);
            return;
        }

        if (registeredCommands.containsKey(commandName)) {
            plugin.getLogger().warning("Command " + commandName + " is already registered.");
            return;
        }

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

    /**
     * Get all registered commands of the feature
     */
    public Map<String, FeatureCommand> getRegisteredCommands() {
        return registeredCommands;
    }

    public int getRegisteredCommandCount() {
        return registeredCommands.size();
    }

}
