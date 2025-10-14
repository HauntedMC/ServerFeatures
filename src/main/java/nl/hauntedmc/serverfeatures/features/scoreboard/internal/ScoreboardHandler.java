package nl.hauntedmc.serverfeatures.features.scoreboard.internal;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.gui.scoreboard.ScoreboardManager;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.serverfeatures.features.scoreboard.Scoreboard;
import nl.hauntedmc.serverfeatures.framework.localization.LocalizationHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates the localized lines each tick and delegates to ScoreboardManager.
 */
public class ScoreboardHandler {
    private static final int MAX_LINES = 15;

    private final Scoreboard feature;
    private final LocalizationHandler i18n;
    private final Map<Player, List<Component>> lastScoreboardLines = new ConcurrentHashMap<>();
    private final int refreshInterval;

    public ScoreboardHandler(Scoreboard feature) {
        this.feature = feature;
        this.i18n = feature.getLocalizationHandler();
        this.refreshInterval = (int) feature.getConfigHandler().getSetting("refresh_interval");
    }

    /**
     * Immediately recalc & push the sidebar for one player
     */
    public void updateScoreboardContent(Player player) {
        Component title = i18n.getMessage("scoreboard.title").forAudience(player).build();
        List<Component> lines = new ArrayList<>();
        for (int i = 1; i <= MAX_LINES; i++) {
            Component c = i18n.getMessage("scoreboard.line" + i).forAudience(player).build();
            String s = ComponentFormatter.serialize(c).format(ComponentFormatter.Serializer.Format.PLAIN).build();
            if (s.startsWith("<end>")) break;
            lines.add(c);
        }

        List<Component> oldLines = lastScoreboardLines.get(player);
        if (oldLines != null && oldLines.equals(lines)) {
            return;
        }
        lastScoreboardLines.put(player, new ArrayList<>(lines));
        ScoreboardManager.updateSidebar(player, title, lines, oldLines);
    }

    /**
     * Runs forceUpdate once every `refreshInterval` ticks for all online players
     */
    public void startUpdater() {
        feature.getLifecycleManager().getTaskManager()
                .scheduleRepeatingTask(() ->
                                Bukkit.getOnlinePlayers().forEach(this::updateScoreboardContent),
                        BukkitTime.ticks(0L), BukkitTime.ticks(refreshInterval));
    }

    /**
     * Removes the player's scoreboard from the handler.
     *
     * @param player the player to remove
     */
    public void removePlayer(Player player) {
        lastScoreboardLines.remove(player);
        ScoreboardManager.removeSidebar(player);
    }

    /**
     * Removes all players from scoreboard tracking and resets their scoreboards to the main scoreboard.
     */
    public void removeAllPlayers() {
        lastScoreboardLines.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            ScoreboardManager.removeSidebar(player);
        }
    }
}
