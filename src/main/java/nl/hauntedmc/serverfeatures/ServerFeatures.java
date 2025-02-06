package nl.hauntedmc.serverfeatures;

import org.bukkit.plugin.java.JavaPlugin;

public class ServerFeatures extends JavaPlugin {

    @Override
    public void onEnable() {
        this.getLogger().info("ServerFeatures wordt geladen.");
        initializeConfig();
        registerPluginHooks();
        registerListeners();
        registerCommands();
    }


    private void initializeConfig() {
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();
    }

    private void registerPluginHooks() {
    }

    private void registerListeners() {
    }

    private void registerCommands() {
    }

    @Override
    public void onDisable() {
        this.getServer().getScheduler().cancelTasks(this);
    }
}
