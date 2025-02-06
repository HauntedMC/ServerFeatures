package nl.hauntedmc.serverfeatures.lifecycle;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class FeatureLifecycleManager {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final List<Listener> registeredListeners = new ArrayList<>();
    private final List<String> registeredCommands = new ArrayList<>();
    private final FeatureTaskManager taskManager;

    public FeatureLifecycleManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.taskManager = new FeatureTaskManager(plugin);
    }

    /**
     * Registers an event listener and tracks it for later removal.
     */
    public void registerListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        registeredListeners.add(listener);
    }

    /**
     * Registers a command and tracks it for later removal.
     */
    public void registerCommand(String command, CommandExecutor executor) {
        PluginCommand cmd = plugin.getCommand(command);
        if (cmd != null) {
            cmd.setExecutor(executor);
            registeredCommands.add(command);
        } else {
            logger.warning("Command " + command + " not found in plugin.yml!");
        }
    }

    /**
     * Provides access to the task manager.
     */
    public FeatureTaskManager getTaskManager() {
        return taskManager;
    }

    /**
     * Cleans up all registered listeners, tasks, and commands.
     */
    public void cleanup() {
        registeredListeners.forEach(HandlerList::unregisterAll);
        registeredListeners.clear();

        taskManager.cancelAllTasks();

        registeredCommands.forEach(cmd -> {
            PluginCommand command = plugin.getCommand(cmd);
            if (command != null) command.setExecutor(null);
        });
        registeredCommands.clear();
    }
}
