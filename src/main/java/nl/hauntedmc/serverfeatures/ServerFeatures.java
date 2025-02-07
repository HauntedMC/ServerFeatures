package nl.hauntedmc.serverfeatures;

import nl.hauntedmc.serverfeatures.commands.ServerFeaturesCommand;
import nl.hauntedmc.serverfeatures.config.ConfigHandler;
import nl.hauntedmc.serverfeatures.lifecycle.FeatureLoadManager;
import nl.hauntedmc.serverfeatures.localization.LocalizationHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class ServerFeatures extends JavaPlugin {

    private ConfigHandler configHandler;
    private FeatureLoadManager featureLoadManager;
    private LocalizationHandler localizationHandler;

    @Override
    public void onEnable() {
        configHandler = new ConfigHandler(this);
        localizationHandler = new LocalizationHandler(this);
        featureLoadManager = new FeatureLoadManager(this);
        registerBaseCommand();
        featureLoadManager.initializeFeatures();
    }

    private void registerBaseCommand() {
        Objects.requireNonNull(getCommand("serverfeatures")).setExecutor(new ServerFeaturesCommand(this));
        Objects.requireNonNull(getCommand("serverfeatures")).setTabCompleter(new ServerFeaturesCommand(this));
    }

    public FeatureLoadManager getFeatureLoadManager() {
        return featureLoadManager;
    }

    public ConfigHandler getConfigHandler() {
        return configHandler;
    }

    public LocalizationHandler getLocalizationHandler() {
        return localizationHandler;
    }

    @Override
    public void onDisable() {
        featureLoadManager.unloadAllFeatures();
        getLogger().info("ServerFeatures is shutting down...");
    }
}