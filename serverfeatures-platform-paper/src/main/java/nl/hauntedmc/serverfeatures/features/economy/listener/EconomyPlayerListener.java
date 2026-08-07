package nl.hauntedmc.serverfeatures.features.economy.listener;

import nl.hauntedmc.serverfeatures.features.economy.Economy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

public final class EconomyPlayerListener implements Listener {
    private final Economy feature;

    public EconomyPlayerListener(Economy feature) {
        this.feature = Objects.requireNonNull(feature, "feature");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        feature.service().preload(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        feature.service().evict(event.getPlayer().getUniqueId());
    }
}
