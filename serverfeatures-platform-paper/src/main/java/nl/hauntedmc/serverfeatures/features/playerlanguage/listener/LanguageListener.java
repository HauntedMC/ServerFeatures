package nl.hauntedmc.serverfeatures.features.playerlanguage.listener;

import nl.hauntedmc.serverfeatures.features.playerlanguage.PlayerLanguage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class LanguageListener implements Listener {

    private final PlayerLanguage feature;

    public LanguageListener(PlayerLanguage feature) {
        this.feature = feature;
    }

    /**
     * Complete the remote language lookup before Bukkit begins firing join listeners.
     * Blocking is safe here: this event is explicitly asynchronous, and a fallback is
     * cached if the lookup cannot produce a configured language.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        feature.getService().warm(event.getUniqueId()).toCompletableFuture().join();
    }

    /**
     * Refresh after join as a safeguard for implementations that update the identity
     * immediately after pre-login. The pre-login warm-up is what guarantees first UI.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent e) {
        feature.getService().warm(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        feature.getService().forget(e.getPlayer().getUniqueId());
    }
}
