package nl.hauntedmc.serverfeatures;

import com.github.retrooper.packetevents.PacketEvents;
import nl.hauntedmc.serverfeatures.commands.ServerFeaturesCommand;
import nl.hauntedmc.serverfeatures.common.listener.ServerPlayerStateListener;
import nl.hauntedmc.serverfeatures.config.MainConfigHandler;
import nl.hauntedmc.serverfeatures.internal.FeatureLoadManager;
import nl.hauntedmc.serverfeatures.localization.LocalizationHandler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class ServerFeatures extends JavaPlugin {

    private MainConfigHandler mainConfigHandler;
    private FeatureLoadManager featureLoadManager;
    private LocalizationHandler localizationHandler;

    @Override
    public void onLoad() {
    }

    @Override
    public void onEnable() {
        PacketEvents.getAPI().init();

        // General plugin initialization
        mainConfigHandler = new MainConfigHandler(this);
        localizationHandler = new LocalizationHandler(this);
        featureLoadManager = new FeatureLoadManager(this);
        registerBaseCommand();
        registerCommonListeners();

        // Feature specific initialization
        featureLoadManager.initializeFeatures();

    }

    @Override
    public void onDisable() {
        featureLoadManager.unloadAllFeatures();
        getLogger().info("ServerFeatures is shutting down...");
    }

    private void registerBaseCommand() {
        Objects.requireNonNull(getCommand("serverfeatures")).setExecutor(new ServerFeaturesCommand(this));
        Objects.requireNonNull(getCommand("serverfeatures")).setTabCompleter(new ServerFeaturesCommand(this));
    }

    private void registerCommonListeners() {
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new ServerPlayerStateListener(this), this);
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