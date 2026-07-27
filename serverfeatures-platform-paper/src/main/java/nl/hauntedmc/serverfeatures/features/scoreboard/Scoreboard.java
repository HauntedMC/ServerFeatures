package nl.hauntedmc.serverfeatures.features.scoreboard;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.scoreboard.internal.ScoreboardHandler;
import nl.hauntedmc.serverfeatures.features.scoreboard.listener.PlayerJoinListener;
import nl.hauntedmc.serverfeatures.features.scoreboard.meta.Meta;
import org.bukkit.Bukkit;

public class Scoreboard extends BukkitBaseFeature<Meta> {

    private ScoreboardHandler scoreboardHandler;

    public Scoreboard(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("refresh_interval", 100);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("scoreboard.title", "[Title]");
        messages.add("scoreboard.line1", "empty1");
        messages.add("scoreboard.line2", "empty2");
        messages.add("scoreboard.line3", "empty3");
        messages.add("scoreboard.line4", "");
        messages.add("scoreboard.line5", "");
        messages.add("scoreboard.line6", "");
        messages.add("scoreboard.line7", "");
        messages.add("scoreboard.line8", "");
        messages.add("scoreboard.line9", "");
        messages.add("scoreboard.line10", "");
        messages.add("scoreboard.line11", "");
        messages.add("scoreboard.line12", "");
        messages.add("scoreboard.line13", "");
        messages.add("scoreboard.line14", "");
        messages.add("scoreboard.line15", "");
        return messages;
    }

    @Override
    public void initialize() {
        scoreboardHandler = new ScoreboardHandler(this);
        scoreboardHandler.startUpdater();
        getLifecycleManager().getListenerManager().registerListener(new PlayerJoinListener(scoreboardHandler));

        // Initialize each online player independently so one broken expansion cannot abort the feature.
        Bukkit.getOnlinePlayers().forEach(scoreboardHandler::updateScoreboardSafely);
    }

    @Override
    public void disable() {
        scoreboardHandler.removeAllPlayers();
    }
}
