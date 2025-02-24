package nl.hauntedmc.serverfeatures.features.tablist.listener;

import nl.hauntedmc.serverfeatures.features.tablist.Tablist;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TablistListener implements Listener {

    private final Tablist feature;

    public TablistListener(Tablist feature) {
        this.feature = feature;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        feature.getHandler().initTablist(event.getPlayer());
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        feature.getHandler().clearTablist(event.getPlayer());
    }
}
