package nl.hauntedmc.serverfeatures;

import com.github.retrooper.packetevents.PacketEvents;
import nl.hauntedmc.commonlib.featureapi.FeaturePlugin;
import nl.hauntedmc.serverfeatures.internal.command.ServerFeaturesCommand;
import nl.hauntedmc.serverfeatures.internal.listener.ScoreboardListener;
import nl.hauntedmc.serverfeatures.internal.config.MainConfigHandler;
import nl.hauntedmc.serverfeatures.internal.FeatureLoadManager;
import nl.hauntedmc.serverfeatures.internal.localization.LocalizationHandler;
import nl.hauntedmc.serverfeatures.api.gui.scoreboard.ScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class ServerFeatures extends JavaPlugin implements FeaturePlugin {

    private MainConfigHandler mainConfigHandler;
    private FeatureLoadManager featureLoadManager;
    private LocalizationHandler localizationHandler;

    @Override
    public void onLoad() {
    }

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("packetevents") != null) {
            PacketEvents.getAPI().init();
        }

        // General plugin initialization
        mainConfigHandler = new MainConfigHandler(this);
        localizationHandler = new LocalizationHandler(this);
        featureLoadManager = new FeatureLoadManager(this);
        registerBaseCommand();
        registerCommonListeners();

        try {
            ScoreboardManager.initializeOnlinePlayers(getLogger());
        } catch (Throwable t) {
            getLogger().warning("Scoreboard shutdown cleanup error: " + t.getMessage());
        }

        // Feature specific initialization
        featureLoadManager.initializeFeatures();
    }

    @Override
    public void onDisable() {
        featureLoadManager.unloadAllFeatures();

        try {
            ScoreboardManager.cleanupOnlinePlayers(getLogger());
        } catch (Throwable t) {
            getLogger().warning("Scoreboard shutdown cleanup error: " + t.getMessage());
        }
        getLogger().info("ServerFeatures is shutting down...");
    }

    private void registerBaseCommand() {
        ServerFeaturesCommand cmd = new ServerFeaturesCommand(this);
        Objects.requireNonNull(getCommand("serverfeatures")).setExecutor(cmd);
        Objects.requireNonNull(getCommand("serverfeatures")).setTabCompleter(cmd);
    }

    private void registerCommonListeners() {
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new ScoreboardListener(this), this);
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
}
