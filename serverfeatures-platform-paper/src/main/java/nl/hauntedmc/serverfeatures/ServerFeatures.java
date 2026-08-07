package nl.hauntedmc.serverfeatures;

import com.github.retrooper.packetevents.PacketEvents;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;
import nl.hauntedmc.serverfeatures.api.RuntimeState;
import nl.hauntedmc.serverfeatures.api.ServerFeaturesApi;
import nl.hauntedmc.serverfeatures.api.ServerFeaturesApiVersion;
import nl.hauntedmc.serverfeatures.api.feature.FeatureCatalog;
import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigService;
import nl.hauntedmc.serverfeatures.api.service.CapabilityRegistry;
import nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.ActionBars;
import nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.impl.PaperActionBarAPI;
import nl.hauntedmc.serverfeatures.api.ui.hud.scoreboard.ScoreboardManager;
import nl.hauntedmc.serverfeatures.framework.command.ServerFeaturesCommand;
import nl.hauntedmc.serverfeatures.framework.command.brigadier.BrigadierDispatcher;
import nl.hauntedmc.serverfeatures.framework.config.MainConfigHandler;
import nl.hauntedmc.serverfeatures.framework.feature.FeatureScopeFactory;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureLifecycleFactory;
import nl.hauntedmc.serverfeatures.framework.listener.PreviewUIListener;
import nl.hauntedmc.serverfeatures.framework.listener.ScoreboardListener;
import nl.hauntedmc.serverfeatures.framework.loader.FeatureLoadManager;
import nl.hauntedmc.serverfeatures.framework.localization.LocalizationHandler;
import nl.hauntedmc.serverfeatures.framework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.serverfeatures.framework.service.DefaultFeatureCatalog;
import nl.hauntedmc.serverfeatures.framework.service.InternalServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

/** Paper bootstrap and authoritative public API root for ServerFeatures 3.3. */
public class ServerFeatures extends JavaPlugin implements ServerFeaturesApi {

    private final DefaultCapabilityRegistry capabilityRegistry = new DefaultCapabilityRegistry();
    private final DefaultFeatureCatalog featureCatalog = new DefaultFeatureCatalog();
    private final InternalServiceRegistry internalServiceRegistry = new InternalServiceRegistry();
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private volatile RuntimeState runtimeState = RuntimeState.STARTING;

    private MainConfigHandler mainConfigHandler;
    private FeatureLoadManager featureLoadManager;
    private LocalizationHandler localizationHandler;
    private ConfigService configService;
    private FeatureLifecycleFactory featureLifecycleFactory;
    private FeatureScopeFactory featureScopeFactory;
    private BrigadierDispatcher brigadierDispatcher;

    @Override
    public void onEnable() {
        runtimeState = RuntimeState.STARTING;
        try {
            if (Bukkit.getPluginManager().getPlugin(BaseMeta.PACKET_EVENTS) != null) {
                PacketEvents.getAPI().init();
            }

            configService = new ConfigService(this);
            mainConfigHandler = new MainConfigHandler(this, configService);
            localizationHandler = new LocalizationHandler(this, configService);

            brigadierDispatcher = new BrigadierDispatcher(this);
            brigadierDispatcher.resolveDispatcher();

            featureLifecycleFactory = new FeatureLifecycleFactory(this);
            featureScopeFactory = new FeatureScopeFactory(
                    this,
                    mainConfigHandler,
                    localizationHandler,
                    featureLifecycleFactory
            );
            featureLoadManager = FeatureLoadManager.create(this);

            registerFrameworkCommand();
            registerFrameworkListeners();

            try {
                ScoreboardManager.initializeOnlinePlayers(getLogger());
            } catch (Throwable throwable) {
                getLogger().warning("Scoreboard init error: " + throwable.getMessage());
            }

            ActionBars.bootstrap(new PaperActionBarAPI(this));
            featureLoadManager.initializeFeatures();

            runtimeState = RuntimeState.READY;
            ready.complete(null);
        } catch (Throwable failure) {
            runtimeState = RuntimeState.DEGRADED;
            ready.completeExceptionally(failure);
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("ServerFeatures startup failed", failure);
        }
    }

    private void registerFrameworkCommand() {
        this.getLifecycleManager().registerEventHandler(
                LifecycleEvents.COMMANDS,
                commands -> commands.registrar().register(new ServerFeaturesCommand(this).buildTree())
        );
    }

    @Override
    public void onDisable() {
        runtimeState = RuntimeState.STOPPING;
        Throwable failure = null;
        try {
            if (featureLoadManager != null) {
                featureLoadManager.unloadAllFeatures();
            }

            try {
                ScoreboardManager.cleanupOnlinePlayers(getLogger());
            } catch (Throwable throwable) {
                getLogger().warning("Scoreboard cleanup error: " + throwable.getMessage());
            }

            if (ActionBars.service() instanceof PaperActionBarAPI paperActionBar) {
                paperActionBar.shutdown();
            }
            ActionBars.shutdown();
        } catch (Throwable throwable) {
            failure = throwable;
            getLogger().log(Level.SEVERE, "ServerFeatures shutdown completed with failures.", throwable);
        } finally {
            runtimeState = RuntimeState.STOPPED;
            if (!ready.isDone()) {
                ready.completeExceptionally(failure == null
                        ? new IllegalStateException("ServerFeatures stopped before becoming ready")
                        : failure);
            }
            getLogger().info("ServerFeatures is shutting down...");
        }
    }

    private void registerFrameworkListeners() {
        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(new ScoreboardListener(this), this);
        pluginManager.registerEvents(new PreviewUIListener(), this);
    }

    @Override
    public ServerFeaturesApiVersion version() {
        return ServerFeaturesApiVersion.current(getPluginMeta().getVersion());
    }

    @Override
    public RuntimeState state() {
        return runtimeState;
    }

    @Override
    public CompletionStage<Void> whenReady() {
        return ready;
    }

    @Override
    public CapabilityRegistry capabilities() {
        return capabilityRegistry;
    }

    @Override
    public FeatureCatalog features() {
        return featureCatalog;
    }

    public DefaultCapabilityRegistry getCapabilityRegistry() {
        return capabilityRegistry;
    }

    public DefaultFeatureCatalog getFeatureCatalog() {
        return featureCatalog;
    }

    public InternalServiceRegistry getInternalServiceRegistry() {
        return internalServiceRegistry;
    }

    public FeatureLoadManager getFeatureLoadManager() {
        return featureLoadManager;
    }

    public MainConfigHandler getConfigHandler() {
        return mainConfigHandler;
    }

    public LocalizationHandler getLocalizationHandler() {
        return localizationHandler;
    }

    public ConfigService getConfigService() {
        return configService;
    }

    public FeatureLifecycleFactory getFeatureLifecycleFactory() {
        return featureLifecycleFactory;
    }

    public FeatureScopeFactory getFeatureScopeFactory() {
        return featureScopeFactory;
    }

    public BrigadierDispatcher getBrigadierDispatcher() {
        return brigadierDispatcher;
    }

    public Optional<DataRegistryApi> getDataRegistry() {
        return Optional.ofNullable(Bukkit.getPluginManager().getPlugin("DataRegistry"))
                .filter(DataRegistryApiProvider.class::isInstance)
                .map(DataRegistryApiProvider.class::cast)
                .map(DataRegistryApiProvider::getDataRegistry);
    }
}
