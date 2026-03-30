package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;

public class FeatureLifecycleManager {

    private final FeatureTaskManager taskManager;
    private final FeatureCommandManager commandManager;
    private final FeatureListenerManager listenerManager;
    private final FeatureDataManager dataManager;
    private final FeatureCacheManager cacheManager;
    private final FeatureGUIManager guiManager;

    public FeatureLifecycleManager(ServerFeatures plugin) {
        this.taskManager = new FeatureTaskManager(plugin);
        this.commandManager = new FeatureCommandManager(plugin);
        this.listenerManager = new FeatureListenerManager(plugin);
        this.dataManager = plugin.getServer().getPluginManager().isPluginEnabled("DataProvider")
                ? new FeatureDataManager(plugin)
                : null;
        this.cacheManager = new FeatureCacheManager(plugin);
        this.guiManager = new FeatureGUIManager(plugin, taskManager);
        this.listenerManager.registerListener(guiManager);
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
     * Provides access to the data manager.
     */
    public FeatureDataManager getDataManager() {
        if (dataManager == null) {
            throw new IllegalStateException("DataProvider is not enabled; data manager is unavailable.");
        }
        return dataManager;
    }

    /**
     * Access to the cache manager for this feature.
     */
    public FeatureCacheManager getCacheManager() {
        return cacheManager;
    }

    /**
     * Per-feature GUI manager
     */
    public FeatureGUIManager getGuiManager() {
        return guiManager;
    }

    /**
     * Cleans up all registered listeners, tasks, and commands.
     */
    public void cleanup() {
        guiManager.shutdown();
        listenerManager.unregisterAllListeners();
        taskManager.cancelAllTasks();
        commandManager.unregisterAllFeatureCommands();
        commandManager.unregisterAllBrigadierCommands();
        if (dataManager != null) {
            dataManager.closeAllConnections();
        }
        cacheManager.cleanupAll();
    }
}
