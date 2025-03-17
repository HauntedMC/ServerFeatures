package nl.hauntedmc.serverfeatures.features.scoreboard.internal;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import nl.hauntedmc.serverfeatures.features.scoreboard.Scoreboard;
import nl.hauntedmc.serverfeatures.localization.LocalizationHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles updating player scoreboards by managing creation, refreshing, and cleanup of scoreboard data.
 */
public class ScoreboardHandler implements Listener {

    private static final int MAX_LINES = 15;
    private static final String OBJECTIVE_NAME = "Scoreboard";
    private static final String OBJECTIVE_CRITERIA = "dummy";

    private final Scoreboard feature;
    private final LocalizationHandler localizationHandler;
    private final Map<Player, org.bukkit.scoreboard.Scoreboard> playerScoreboards = new ConcurrentHashMap<>();
    private final Map<Player, List<Component>> lastScoreboardLines = new ConcurrentHashMap<>();
    private final int refreshInterval;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();

    public ScoreboardHandler(Scoreboard feature) {
        this.feature = feature;
        this.localizationHandler = feature.getLocalizationHandler();
        this.refreshInterval = (int) feature.getConfigHandler().getSetting("refresh_interval");
        Bukkit.getPluginManager().registerEvents(this, feature.getPlugin());
    }

    /**
     * Updates the scoreboard for the specified player.
     *
     * @param player the player whose scoreboard should be updated
     */
    public void updateScoreboard(Player player) {
        org.bukkit.scoreboard.Scoreboard scoreboard = getOrCreatePlayerScoreboard(player);
        Objective objective = getOrCreateObjective(scoreboard, player);

        // Update objective title based on player's locale
        updateObjectiveTitle(objective, player);

        // Generate the list of new scoreboard lines
        List<Component> newLines = getProcessedScoreboardLines(player);
        List<Component> oldLines = lastScoreboardLines.get(player);

        // If no changes, skip updating to reduce overhead.
        if (oldLines != null && oldLines.equals(newLines)) {
            return;
        }

        // Update individual score lines
        updateScoreLines(objective, newLines, oldLines);
        // Clean up any lines that are no longer used
        cleanupExtraLines(scoreboard, newLines, oldLines);

        // Cache the new lines and assign the updated scoreboard to the player.
        lastScoreboardLines.put(player, new ArrayList<>(newLines));
        player.setScoreboard(scoreboard);
    }

    /**
     * Retrieves an existing scoreboard for the player or creates a new one if none exists.
     *
     * @param player the player for whom to retrieve/create the scoreboard
     * @return the player's scoreboard
     */
    private org.bukkit.scoreboard.Scoreboard getOrCreatePlayerScoreboard(Player player) {
        return playerScoreboards.computeIfAbsent(player, p -> {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            return manager.getNewScoreboard();
        });
    }

    /**
     * Retrieves an existing objective from the scoreboard or creates a new one if not found.
     *
     * @param scoreboard the scoreboard to check
     * @param player     the player for localization purposes
     * @return the scoreboard objective
     */
    private Objective getOrCreateObjective(org.bukkit.scoreboard.Scoreboard scoreboard, Player player) {
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = scoreboard.registerNewObjective(
                    OBJECTIVE_NAME,
                    OBJECTIVE_CRITERIA,
                    localizationHandler.getMessage("scoreboard.title", player)
            );
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        return objective;
    }

    /**
     * Updates the display title of the objective.
     *
     * @param objective the objective to update
     * @param player    the player for which the title should be localized
     */
    private void updateObjectiveTitle(Objective objective, Player player) {
        Component title = localizationHandler.getMessage("scoreboard.title", player);
        objective.displayName(title);
    }

    /**
     * Updates or creates score lines for the scoreboard.
     *
     * @param objective the objective to update
     * @param newLines  the new list of scoreboard lines
     * @param oldLines  the previous list of scoreboard lines
     */
    private void updateScoreLines(Objective objective, List<Component> newLines, List<Component> oldLines) {
        for (int i = 0; i < newLines.size(); i++) {
            int scoreValue = newLines.size() - i;
            String lineKey = "line" + i;
            Score score = objective.getScore(lineKey);

            boolean needsUpdate = oldLines == null || i >= oldLines.size() || !oldLines.get(i).equals(newLines.get(i));
            if (needsUpdate) {
                score.setScore(scoreValue);
                score.customName(newLines.get(i));
                score.numberFormat(NumberFormat.blank());
            }
        }
    }

    /**
     * Removes score entries that are no longer needed.
     *
     * @param scoreboard the scoreboard to clean up
     * @param newLines   the new list of scoreboard lines
     * @param oldLines   the previous list of scoreboard lines
     */
    private void cleanupExtraLines(org.bukkit.scoreboard.Scoreboard scoreboard, List<Component> newLines, List<Component> oldLines) {
        if (oldLines != null && oldLines.size() > newLines.size()) {
            for (int i = newLines.size(); i < oldLines.size(); i++) {
                String lineKey = "line" + i;
                scoreboard.resetScores(lineKey);
            }
        }
    }

    /**
     * Processes and retrieves the scoreboard lines from localization, stopping early if an <code>&lt;end&gt;</code> marker is found.
     *
     * @param player the player for which to process the lines
     * @return a list of processed scoreboard lines
     */
    private List<Component> getProcessedScoreboardLines(Player player) {
        List<Component> processedLines = new ArrayList<>(MAX_LINES);
        for (int i = 1; i <= MAX_LINES; i++) {
            Component message = localizationHandler.getMessage("scoreboard.line" + i, player);
            String msg = serializer.serialize(message);

            if (msg.startsWith("<end>")) {
                break;
            }
            processedLines.add(message);
        }
        return processedLines;
    }

    /**
     * Forces an immediate update of the player's scoreboard.
     *
     * @param player the player whose scoreboard should be updated immediately
     */
    public void forceUpdate(Player player) {
        updateScoreboard(player);
    }

    /**
     * Starts the periodic update task for all online players.
     */
    public void startUpdateTask() {
        feature.getLifecycleManager().getTaskManager().scheduleDelayedRepeatingTask(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateScoreboard(player);
            }
        }, 0, refreshInterval);
    }

    /**
     * Removes the player's scoreboard from the handler.
     *
     * @param player the player to remove
     */
    public void removePlayer(Player player) {
        playerScoreboards.remove(player);
        lastScoreboardLines.remove(player);
    }

    /**
     * Removes all players from scoreboard tracking and resets their scoreboards to the main scoreboard.
     */
    public void removeAllPlayers() {
        playerScoreboards.clear();
        lastScoreboardLines.clear();
        Bukkit.getOnlinePlayers().forEach(player ->
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard())
        );
    }
}
