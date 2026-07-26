package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import nl.hauntedmc.serverfeatures.framework.service.FeatureServiceCatalog;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Constructs independent lifecycle resource sets for feature instances.
 */
public final class FeatureLifecycleFactory {

    private final Supplier<FeatureTaskManager> taskManagerFactory;
    private final Supplier<FeatureCommandManager> commandManagerFactory;
    private final Supplier<FeatureListenerManager> listenerManagerFactory;
    private final Supplier<FeatureDataManager> dataManagerFactory;
    private final Supplier<FeatureCacheManager> cacheManagerFactory;
    private final Function<FeatureTaskManager, FeatureGUIManager> guiManagerFactory;
    private final Function<String, FeatureApiManager> apiManagerFactory;
    private final FeatureServiceCatalog serviceCatalog;

    public FeatureLifecycleFactory(ServerFeatures plugin) {
        Objects.requireNonNull(plugin, "plugin");
        FeatureCommandOwnership ownership = new FeatureCommandOwnership();
        this.serviceCatalog = new FeatureServiceCatalog();
        this.taskManagerFactory = () -> new FeatureTaskManager(plugin);
        this.commandManagerFactory = () -> new FeatureCommandManager(plugin, ownership);
        this.listenerManagerFactory = () -> new FeatureListenerManager(plugin);
        this.dataManagerFactory = () -> plugin.getServer().getPluginManager().isPluginEnabled(BaseMeta.DATA_PROVIDER)
                ? new FeatureDataManager(plugin)
                : null;
        this.cacheManagerFactory = () -> new FeatureCacheManager(plugin);
        this.guiManagerFactory = tasks -> new FeatureGUIManager(plugin, tasks);
        this.apiManagerFactory = name -> new FeatureApiManager(name, plugin::getDataRegistry, serviceCatalog);
    }

    FeatureLifecycleFactory(
            Supplier<FeatureTaskManager> taskManagerFactory,
            Supplier<FeatureCommandManager> commandManagerFactory,
            Supplier<FeatureListenerManager> listenerManagerFactory,
            Supplier<FeatureDataManager> dataManagerFactory,
            Supplier<FeatureCacheManager> cacheManagerFactory,
            Function<FeatureTaskManager, FeatureGUIManager> guiManagerFactory,
            Function<String, FeatureApiManager> apiManagerFactory
    ) {
        this.serviceCatalog = new FeatureServiceCatalog();
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
        return new FeatureLifecycleManager(
                taskManager,
                commandManagerFactory.get(),
                listenerManagerFactory.get(),
                dataManagerFactory.get(),
                cacheManagerFactory.get(),
                guiManagerFactory.apply(taskManager),
                apiManagerFactory.apply(featureName)
        );
    }

    public <T> Optional<T> findService(Class<T> type) {
        return serviceCatalog.find(type);
    }
}
