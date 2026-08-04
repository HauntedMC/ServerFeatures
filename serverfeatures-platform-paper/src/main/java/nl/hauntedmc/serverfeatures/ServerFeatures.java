package nl.hauntedmc.serverfeatures;

import com.github.retrooper.packetevents.PacketEvents;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;
import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigService;
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
import nl.hauntedmc.serverfeatures.framework.time.ServerActiveClock;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public class ServerFeatures extends JavaPlugin {

    private MainConfigHandler mainConfigHandler;
    private FeatureLoadManager featureLoadManager;
    private LocalizationHandler localizationHandler;
    private ConfigService configService;
    private FeatureLifecycleFactory featureLifecycleFactory;
    private FeatureScopeFactory featureScopeFactory;
    private ServerActiveClock serverActiveClock;

    // Keep if other parts of your framework still use dispatcher (features registering their own brig nodes).
    private BrigadierDispatcher brigadierDispatcher;


    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin(BaseMeta.PACKET_EVENTS) != null) {
            PacketEvents.getAPI().init();
        }

        configService = new ConfigService(this);
        mainConfigHandler = new MainConfigHandler(this, configService);
        localizationHandler = new LocalizationHandler(this, configService);
        featureLifecycleFactory = new FeatureLifecycleFactory(this);
        featureScopeFactory = new FeatureScopeFactory(
                this,
                mainConfigHandler,
                localizationHandler,
                featureLifecycleFactory
        );
        serverActiveClock = new ServerActiveClock(this);
        serverActiveClock.start();
        featureLoadManager = FeatureLoadManager.create(this);

        // Optional: if your feature system still needs direct dispatcher access elsewhere
        brigadierDispatcher = new BrigadierDispatcher(this);
        brigadierDispatcher.resolveDispatcher();

        registerFrameworkCommand();
        registerFrameworkListeners();

        try {
            ScoreboardManager.initializeOnlinePlayers(getLogger());
        } catch (Throwable t) {
            getLogger().warning("Scoreboard init error: " + t.getMessage());
        }

        ActionBars.bootstrap(new PaperActionBarAPI(this));

        featureLoadManager.initializeFeatures();
    }

    private void registerFrameworkCommand() {
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> commands.registrar().register(new ServerFeaturesCommand(this).buildTree()));
    }

    @Override
    public void onDisable() {
        if (featureLoadManager != null) {
            featureLoadManager.unloadAllFeatures();
        }

        try {
            ScoreboardManager.cleanupOnlinePlayers(getLogger());
        } catch (Throwable t) {
            getLogger().warning("Scoreboard cleanup error: " + t.getMessage());
        }

        ((PaperActionBarAPI) ActionBars.service()).shutdown();
        ActionBars.shutdown();

        if (serverActiveClock != null) {
            serverActiveClock.close();
        }

        getLogger().info("ServerFeatures is shutting down...");
    }

    private void registerFrameworkListeners() {
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new ScoreboardListener(this), this);
        pm.registerEvents(new PreviewUIListener(), this);

    }

    /* ============================== ACCESSORS ============================== */
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

    public ServerActiveClock getServerActiveClock() {
        return serverActiveClock;
    }

    public Optional<DataRegistryApi> getDataRegistry() {
        return Optional.ofNullable(Bukkit.getPluginManager().getPlugin("DataRegistry"))
                .filter(DataRegistryApiProvider.class::isInstance)
                .map(DataRegistryApiProvider.class::cast)
                .map(DataRegistryApiProvider::getDataRegistry);
    }
}
