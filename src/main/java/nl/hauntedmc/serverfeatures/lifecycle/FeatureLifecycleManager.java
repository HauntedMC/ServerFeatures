package nl.hauntedmc.serverfeatures.lifecycle;

import nl.hauntedmc.serverfeatures.commands.FeatureCommandManager;
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
    private final FeatureCommandManager commandManager;

    public FeatureLifecycleManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.taskManager = new FeatureTaskManager(plugin);
        this.commandManager = new FeatureCommandManager(plugin);
    }

    /**
     * Registers an event listener and tracks it for later removal.
     */
    public void registerListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        registeredListeners.add(listener);
    }


    /**
     * Provides access to the task manager.
     */
    public FeatureTaskManager getTaskManager() {
        return taskManager;
    }

    /**
     * Provides access to the command manager.
     */
    public FeatureCommandManager getCommandManager() {
        return commandManager;
    }

    /**
     * Cleans up all registered listeners, tasks, and commands.
     */
    public void cleanup() {
        registeredListeners.forEach(HandlerList::unregisterAll);
        registeredListeners.clear();

        taskManager.cancelAllTasks();

        commandManager.unregisterAllCommands();
    }
}
