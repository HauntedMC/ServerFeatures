package nl.hauntedmc.serverfeatures.common.listener;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.scoreboard.ScoreboardManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerListener implements Listener {

    public PlayerListener(ServerFeatures plugin) {
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        ScoreboardManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        ScoreboardManager.onPlayerQuit(event.getPlayer());
    }

}
