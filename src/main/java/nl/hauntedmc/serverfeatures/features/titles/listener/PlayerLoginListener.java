package nl.hauntedmc.serverfeatures.features.titles.listener;

import nl.hauntedmc.serverfeatures.features.titles.Titles;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerLoginListener implements Listener {

    private final Titles feature;

    public PlayerLoginListener(Titles feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        this.feature.getTitleHandler().sendJoinTitle(event.getPlayer());
    }
}
