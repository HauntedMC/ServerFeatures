package nl.hauntedmc.serverfeatures;

import com.github.retrooper.packetevents.PacketEvents;
import nl.hauntedmc.commonlib.featureapi.FeaturePlugin;
import nl.hauntedmc.serverfeatures.api.command.tab.TabService;
import nl.hauntedmc.serverfeatures.api.command.tab.TabTree;
import nl.hauntedmc.serverfeatures.framework.command.ServerFeaturesCommand;
import nl.hauntedmc.serverfeatures.framework.listener.ScoreboardListener;
import nl.hauntedmc.serverfeatures.framework.config.MainConfigHandler;
import nl.hauntedmc.serverfeatures.framework.loader.FeatureLoadManager;
import nl.hauntedmc.serverfeatures.framework.listener.TabCompleteListener;
import nl.hauntedmc.serverfeatures.framework.localization.LocalizationHandler;
import nl.hauntedmc.serverfeatures.api.gui.scoreboard.ScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ServerFeatures extends JavaPlugin implements FeaturePlugin {

    private MainConfigHandler mainConfigHandler;
    private FeatureLoadManager featureLoadManager;
    private LocalizationHandler localizationHandler;
    private TabService tabService;

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("packetevents") != null) {
            PacketEvents.getAPI().init();
        }

        // General plugin initialization
        mainConfigHandler = new MainConfigHandler(this);
        localizationHandler = new LocalizationHandler(this);
        featureLoadManager = new FeatureLoadManager(this);
        tabService = new TabService(this);
        registerFrameworkCommand();
        registerFrameworkListeners();

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
        unregisterFrameworkCommand();
        featureLoadManager.unloadAllFeatures();

        try {
            ScoreboardManager.cleanupOnlinePlayers(getLogger());
        } catch (Throwable t) {
            getLogger().warning("Scoreboard shutdown cleanup error: " + t.getMessage());
        }
        getLogger().info("ServerFeatures is shutting down...");
    }

    private void registerFrameworkCommand() {
        PluginCommand pcmd = Objects.requireNonNull(getCommand("serverfeatures"));
        ServerFeaturesCommand cmd = new ServerFeaturesCommand(this);

        // Executor only; tab completions handled by global async listener
        pcmd.setExecutor(cmd);

        // Register rich completions in global TabService for primary + aliases
        TabTree tree = cmd.createTabTree();
        tabService.register(pcmd.getName().toLowerCase(Locale.ROOT), tree);
        for (String a : pcmd.getAliases()) {
            if (a != null && !a.isBlank()) {
                tabService.register(a.toLowerCase(Locale.ROOT), tree);
            }
        }
    }

    private void unregisterFrameworkCommand() {
        PluginCommand pcmd = getCommand("serverfeatures");
        if (pcmd == null) return;

        // Unregister tabs from the global TabService
        final String primary = pcmd.getName().toLowerCase(Locale.ROOT);
        if (tabService != null) {
            tabService.unregister(primary);
            for (String a : pcmd.getAliases()) {
                if (a != null && !a.isBlank()) {
                    tabService.unregister(a.toLowerCase(Locale.ROOT));
                }
            }
        }

        // Unregister the command from the CommandMap (primary + namespaced + aliases)
        CommandMap cmap = getServer().getCommandMap();
        try {
            // Remove from the map and its knownCommands registry
            pcmd.unregister(cmap);

            Map<String, Command> known = cmap.getKnownCommands();
            final String pluginNs = getName().toLowerCase(Locale.ROOT) + ":";

            known.remove(primary);
            known.remove(pluginNs + primary);

            for (String a : pcmd.getAliases()) {
                if (a == null || a.isBlank()) continue;
                final String al = a.toLowerCase(Locale.ROOT);
                known.remove(al);
                known.remove(pluginNs + al);
            }
        } catch (Throwable ignored) {
        }
    }

    private void registerFrameworkListeners() {
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new ScoreboardListener(this), this);
        pm.registerEvents(new TabCompleteListener(tabService), this);
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

    public TabService getTabService() {
        return tabService;
    }
}
