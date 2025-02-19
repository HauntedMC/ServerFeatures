package nl.hauntedmc.serverfeatures.common.listener;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.scoreboard.ScoreboardManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

public class ServerPlayerStateListener implements Listener {

    private final ServerFeatures plugin;

    public ServerPlayerStateListener(ServerFeatures plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        boolean success = ScoreboardManager.addPlayerToTeam(event.getPlayer());
        if (!success) {
            plugin.getLogger().warning("Failed to add player to team.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        boolean success = ScoreboardManager.removePlayerFromTeam(event.getPlayer());
        if (!success) {
            plugin.getLogger().warning("Failed to remove player from team.");
        }
    }

}
