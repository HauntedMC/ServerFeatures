package nl.hauntedmc.serverfeatures.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;

public class FeatureLifecycleManager {

    private final FeatureTaskManager taskManager;
    private final FeatureCommandManager commandManager;

    private final FeatureListenerManager listenerManager;

    public FeatureLifecycleManager(ServerFeatures plugin) {
        this.taskManager = new FeatureTaskManager(plugin);
        this.commandManager = new FeatureCommandManager(plugin);
        this.listenerManager = new FeatureListenerManager(plugin);
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
     * Provides access to the listener manager.
     */
    public FeatureListenerManager getListenerManager() {
        return listenerManager;
    }

    /**
     * Cleans up all registered listeners, tasks, and commands.
     */
    public void cleanup() {
        listenerManager.unregisterAllListeners();
        taskManager.cancelAllTasks();
        commandManager.unregisterAllCommands();
    }
}
