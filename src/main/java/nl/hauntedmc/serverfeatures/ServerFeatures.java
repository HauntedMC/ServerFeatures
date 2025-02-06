package nl.hauntedmc.serverfeatures;

import nl.hauntedmc.serverfeatures.commands.ServerFeaturesCommand;
import nl.hauntedmc.serverfeatures.config.ConfigHandler;
import nl.hauntedmc.serverfeatures.handler.FeatureHandler;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerFeatures extends JavaPlugin {

    private ConfigHandler configHandler;
    private FeatureHandler featureHandler;

    @Override
    public void onEnable() {
        configHandler = new ConfigHandler(this);
        featureHandler = new FeatureHandler(this, configHandler);
        registerCommands();
        featureHandler.initializeAllFeatures();
    }

    private void registerCommands() {
        getCommand("serverfeatures").setExecutor(new ServerFeaturesCommand(this));
        getCommand("serverfeatures").setTabCompleter(new ServerFeaturesCommand(this));
    }

    public FeatureHandler getFeatureHandler() {
        return featureHandler;
    }

    @Override
    public void onDisable() {
        featureHandler.disableAllLoadedFeatures();
        getLogger().info("ServerFeatures is shutting down...");
    }
}