package nl.hauntedmc.serverfeatures.features.playerlanguage.listener;

import nl.hauntedmc.serverfeatures.features.playerlanguage.PlayerLanguage;
import nl.hauntedmc.serverfeatures.framework.persistence.DataRegistryIdentityGate;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class LanguageListener implements Listener {

    private final PlayerLanguage feature;

    public LanguageListener(PlayerLanguage feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        var player = e.getPlayer();
        DataRegistryIdentityGate.runWhenReady(
                feature,
                player,
                readyPlayer -> feature.getService().warm(readyPlayer.getUniqueId()),
                "language warmup"
        );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        feature.getService().forget(e.getPlayer().getUniqueId());
    }
}
