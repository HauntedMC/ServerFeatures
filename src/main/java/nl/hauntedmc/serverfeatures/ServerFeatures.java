package nl.hauntedmc.serverfeatures;

import com.github.retrooper.packetevents.PacketEvents;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.platform.common.PlatformPlugin;
import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.ActionBars;
import nl.hauntedmc.serverfeatures.api.ui.hud.actionbar.impl.PaperActionBarAPI;
import nl.hauntedmc.serverfeatures.api.ui.hud.scoreboard.ScoreboardManager;
import nl.hauntedmc.serverfeatures.framework.command.ServerFeaturesCommand;
import nl.hauntedmc.serverfeatures.framework.command.brigadier.BrigadierDispatcher;
import nl.hauntedmc.serverfeatures.framework.config.MainConfigHandler;
import nl.hauntedmc.serverfeatures.framework.listener.PreviewUIListener;
import nl.hauntedmc.serverfeatures.framework.listener.ScoreboardListener;
import nl.hauntedmc.serverfeatures.framework.loader.FeatureLoadManager;
import nl.hauntedmc.serverfeatures.framework.localization.LocalizationHandler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public class ServerFeatures extends JavaPlugin {

    private MainConfigHandler mainConfigHandler;
    private FeatureLoadManager featureLoadManager;
    private LocalizationHandler localizationHandler;

    // Keep if other parts of your framework still use dispatcher (features registering their own brig nodes).
    private BrigadierDispatcher brigadierDispatcher;


    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin(BaseMeta.PACKET_EVENTS) != null) {
            PacketEvents.getAPI().init();
        }

        mainConfigHandler = new MainConfigHandler(this);
        localizationHandler = new LocalizationHandler(this);
        featureLoadManager = new FeatureLoadManager(this);

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
        featureLoadManager.unloadAllFeatures();

        try {
            ScoreboardManager.cleanupOnlinePlayers(getLogger());
        } catch (Throwable t) {
            getLogger().warning("Scoreboard cleanup error: " + t.getMessage());
        }

        ((PaperActionBarAPI) ActionBars.service()).shutdown();
        ActionBars.shutdown();

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

    public BrigadierDispatcher getBrigadierDispatcher() {
        return brigadierDispatcher;
    }

    public Optional<DataRegistry> getDataRegistry() {
        return Optional.ofNullable(Bukkit.getPluginManager().getPlugin("DataRegistry"))
                .filter(PlatformPlugin.class::isInstance)
                .map(PlatformPlugin.class::cast)
                .map(PlatformPlugin::getDataRegistry);
    }
}
