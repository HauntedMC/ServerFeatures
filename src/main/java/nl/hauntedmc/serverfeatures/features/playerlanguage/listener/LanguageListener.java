package nl.hauntedmc.serverfeatures.features.playerlanguage.listener;

import nl.hauntedmc.serverfeatures.features.playerlanguage.PlayerLanguage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class LanguageListener implements Listener {

    private final PlayerLanguage feature;

    public LanguageListener(PlayerLanguage feature) {
        this.feature = feature;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        feature.getService().warm(e.getPlayer().getUniqueId()); // single DB read
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        feature.getService().forget(e.getPlayer().getUniqueId()); // cleanup
    }
}
