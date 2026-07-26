package nl.hauntedmc.serverfeatures.framework.lifecycle;

import java.util.Objects;

/**
 * Aggregates every runtime resource owned by one loaded feature.
 */
public class FeatureLifecycleManager {

    private final FeatureTaskManager taskManager;
    private final FeatureCommandManager commandManager;
    private final FeatureListenerManager listenerManager;
    private final FeatureDataManager dataManager;
    private final FeatureCacheManager cacheManager;
    private final FeatureGUIManager guiManager;
    private final FeatureApiManager apiManager;

    public FeatureLifecycleManager(
            FeatureTaskManager taskManager,
            FeatureCommandManager commandManager,
            FeatureListenerManager listenerManager,
            FeatureDataManager dataManager,
            FeatureCacheManager cacheManager,
            FeatureGUIManager guiManager,
            FeatureApiManager apiManager
    ) {
        this.taskManager = Objects.requireNonNull(taskManager, "taskManager");
        this.commandManager = Objects.requireNonNull(commandManager, "commandManager");
        this.listenerManager = Objects.requireNonNull(listenerManager, "listenerManager");
        this.dataManager = dataManager;
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager");
        this.guiManager = Objects.requireNonNull(guiManager, "guiManager");
        this.apiManager = Objects.requireNonNull(apiManager, "apiManager");
        this.listenerManager.registerListener(guiManager);
    }

    public FeatureTaskManager getTaskManager() {
        return taskManager;
    }

    public FeatureCommandManager getCommandManager() {
        return commandManager;
    }

    public FeatureListenerManager getListenerManager() {
        return listenerManager;
    }

    public FeatureDataManager getDataManager() {
        if (dataManager == null) {
            throw new IllegalStateException("DataProvider is not enabled; data manager is unavailable.");
        }
        return dataManager;
    }

    public FeatureCacheManager getCacheManager() {
        return cacheManager;
    }

    public FeatureGUIManager getGuiManager() {
        return guiManager;
    }

    public FeatureApiManager getApiManager() {
        return apiManager;
    }

    /**
     * Attempts every cleanup step and rethrows the first failure with later failures suppressed.
     */
    public void cleanup() {
        Throwable failure = null;
        failure = runCleanupStep(failure, guiManager::shutdown);
        failure = runCleanupStep(failure, listenerManager::unregisterAllListeners);
        failure = runCleanupStep(failure, taskManager::cancelAllTasks);
        failure = runCleanupStep(failure, commandManager::unregisterAllFeatureCommands);
        failure = runCleanupStep(failure, commandManager::unregisterAllBrigadierCommands);
        failure = runCleanupStep(failure, apiManager::unregisterAllServices);
        if (dataManager != null) {
            failure = runCleanupStep(failure, dataManager::closeAllConnections);
        }
        failure = runCleanupStep(failure, cacheManager::cleanupAll);
        if (failure != null) {
            throwUnchecked(failure);
        }
    }

    private static Throwable runCleanupStep(Throwable failure, Runnable step) {
        try {
            step.run();
            return failure;
        } catch (Throwable throwable) {
            if (failure == null) {
                return throwable;
            }
            failure.addSuppressed(throwable);
            return failure;
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable throwable) throws E {
        throw (E) throwable;
    }
}
