package nl.hauntedmc.serverfeatures.features.lottery.listener;

import nl.hauntedmc.serverfeatures.features.lottery.Lottery;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class LotteryPlayerListener implements Listener {

    private final Lottery feature;

    public LotteryPlayerListener(Lottery feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (feature.service() != null) {
            feature.service().onJoin(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (feature.service() != null) {
            feature.service().onQuit(event.getPlayer());
        }
    }
}
