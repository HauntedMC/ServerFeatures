package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** Constructs independent lifecycle resource sets for feature instances. */
public final class FeatureLifecycleFactory {
    private final ServerFeatures plugin;
    private final Supplier<FeatureTaskManager> taskManagerFactory;
    private final Supplier<FeatureCommandManager> commandManagerFactory;
    private final Supplier<FeatureListenerManager> listenerManagerFactory;
    private final Supplier<FeatureDataManager> dataManagerFactory;
    private final Supplier<FeatureCacheManager> cacheManagerFactory;
    private final Function<FeatureTaskManager, FeatureGUIManager> guiManagerFactory;
    private final Supplier<FeatureApiManager> apiManagerFactory;

    public FeatureLifecycleFactory(ServerFeatures plugin) {
        Objects.requireNonNull(plugin, "plugin");
        FeatureCommandOwnership ownership = new FeatureCommandOwnership();
        this.plugin = plugin;
        this.taskManagerFactory = () -> new FeatureTaskManager(plugin);
        this.commandManagerFactory = () -> new FeatureCommandManager(plugin, ownership);
        this.listenerManagerFactory = () -> new FeatureListenerManager(plugin);
        this.dataManagerFactory = () -> plugin.getServer().getPluginManager().isPluginEnabled(BaseMeta.DATA_PROVIDER)
                ? new FeatureDataManager(plugin) : null;
        this.cacheManagerFactory = () -> new FeatureCacheManager(plugin);
        this.guiManagerFactory = tasks -> new FeatureGUIManager(plugin, tasks);
        this.apiManagerFactory = FeatureApiManager::new;
    }

    FeatureLifecycleFactory(
            Supplier<FeatureTaskManager> taskManagerFactory,
            Supplier<FeatureCommandManager> commandManagerFactory,
            Supplier<FeatureListenerManager> listenerManagerFactory,
            Supplier<FeatureDataManager> dataManagerFactory,
            Supplier<FeatureCacheManager> cacheManagerFactory,
            Function<FeatureTaskManager, FeatureGUIManager> guiManagerFactory,
            Supplier<FeatureApiManager> apiManagerFactory
    ) {
        this.plugin = null;
        this.taskManagerFactory = Objects.requireNonNull(taskManagerFactory, "taskManagerFactory");
        this.commandManagerFactory = Objects.requireNonNull(commandManagerFactory, "commandManagerFactory");
        this.listenerManagerFactory = Objects.requireNonNull(listenerManagerFactory, "listenerManagerFactory");
        this.dataManagerFactory = Objects.requireNonNull(dataManagerFactory, "dataManagerFactory");
        this.cacheManagerFactory = Objects.requireNonNull(cacheManagerFactory, "cacheManagerFactory");
        this.guiManagerFactory = Objects.requireNonNull(guiManagerFactory, "guiManagerFactory");
        this.apiManagerFactory = Objects.requireNonNull(apiManagerFactory, "apiManagerFactory");
    }

    public FeatureLifecycleManager createLifecycleManager(String featureName) {
        FeatureTaskManager taskManager = taskManagerFactory.get();
        FeatureApiManager apiManager = apiManagerFactory.get();
        if (plugin != null) {
            apiManager.bindRegistry(
                    plugin.getCapabilityRegistry(),
                    plugin.getInternalServiceRegistry(),
                    featureName
            );
        }
        return new FeatureLifecycleManager(
                taskManager,
                commandManagerFactory.get(),
                listenerManagerFactory.get(),
                dataManagerFactory.get(),
                cacheManagerFactory.get(),
                guiManagerFactory.apply(taskManager),
                apiManager
        );
    }
}
