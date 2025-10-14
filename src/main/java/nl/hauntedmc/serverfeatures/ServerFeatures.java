package nl.hauntedmc.serverfeatures;

import com.github.retrooper.packetevents.PacketEvents;
import nl.hauntedmc.serverfeatures.api.gui.scoreboard.ScoreboardManager;
import nl.hauntedmc.serverfeatures.framework.command.ServerFeaturesCommand;
import nl.hauntedmc.serverfeatures.framework.command.brigadier.BrigadierDispatcher;
import nl.hauntedmc.serverfeatures.framework.command.sync.CommandSync;
import nl.hauntedmc.serverfeatures.framework.config.MainConfigHandler;
import nl.hauntedmc.serverfeatures.framework.listener.ScoreboardListener;
import nl.hauntedmc.serverfeatures.framework.loader.FeatureLoadManager;
import nl.hauntedmc.serverfeatures.framework.localization.LocalizationHandler;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ServerFeatures extends JavaPlugin {

    private MainConfigHandler mainConfigHandler;
    private FeatureLoadManager featureLoadManager;
    private LocalizationHandler localizationHandler;
    private BrigadierDispatcher brigadierDispatcher;

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("packetevents") != null) {
            PacketEvents.getAPI().init();
        }

        mainConfigHandler = new MainConfigHandler(this);
        localizationHandler = new LocalizationHandler(this);
        featureLoadManager = new FeatureLoadManager(this);

        brigadierDispatcher = new BrigadierDispatcher(this);
        brigadierDispatcher.resolveDispatcher();

        registerFrameworkCommand();
        registerFrameworkListeners();

        try {
            ScoreboardManager.initializeOnlinePlayers(getLogger());
        } catch (Throwable t) {
            getLogger().warning("Scoreboard shutdown cleanup error: " + t.getMessage());
        }

        featureLoadManager.initializeFeatures();
        CommandSync.apply(this);
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
        CommandSync.apply(this);
    }

    private void registerFrameworkCommand() {
        PluginCommand pcmd = Objects.requireNonNull(getCommand("serverfeatures"));
        ServerFeaturesCommand cmd = new ServerFeaturesCommand(this);
        pcmd.setExecutor(cmd);
        pcmd.setTabCompleter(cmd);
    }

    private void unregisterFrameworkCommand() {
        PluginCommand pcmd = getCommand("serverfeatures");
        if (pcmd == null) return;

        final String primary = pcmd.getName().toLowerCase(Locale.ROOT);
        CommandMap cmap = getServer().getCommandMap();
        try {
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


}
