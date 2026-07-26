package nl.hauntedmc.serverfeatures.features.scoreboard.listener;

import nl.hauntedmc.serverfeatures.features.scoreboard.internal.ScoreboardHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinListener implements Listener {

    private final ScoreboardHandler scoreboardHandler;

    public PlayerJoinListener(ScoreboardHandler scoreboardHandler) {
        this.scoreboardHandler = scoreboardHandler;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        scoreboardHandler.updateScoreboardContent(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        scoreboardHandler.removePlayer(event.getPlayer());
    }

}
