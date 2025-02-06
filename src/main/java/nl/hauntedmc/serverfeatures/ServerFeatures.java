package nl.hauntedmc.serverfeatures;

import nl.hauntedmc.serverfeatures.commands.ServerFeaturesCommand;
import nl.hauntedmc.serverfeatures.config.ConfigHandler;
import nl.hauntedmc.serverfeatures.lifecycle.FeatureLoadManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerFeatures extends JavaPlugin {

    private ConfigHandler configHandler;
    private FeatureLoadManager featureLoadManager;

    @Override
    public void onEnable() {
        configHandler = new ConfigHandler(this);
        featureLoadManager = new FeatureLoadManager(this, configHandler);
        registerCommands();
        featureLoadManager.initializeAllFeatures();
    }

    private void registerCommands() {
        getCommand("serverfeatures").setExecutor(new ServerFeaturesCommand(this));
        getCommand("serverfeatures").setTabCompleter(new ServerFeaturesCommand(this));
    }

    public FeatureLoadManager getFeatureHandler() {
        return featureLoadManager;
    }

    @Override
    public void onDisable() {
        featureLoadManager.disableAllLoadedFeatures();
        getLogger().info("ServerFeatures is shutting down...");
    }
}