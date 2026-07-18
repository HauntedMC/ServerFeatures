package nl.hauntedmc.serverfeatures.features.playerlanguage.listener;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.playerlanguage.PlayerLanguage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class LanguageListener implements Listener {

    private static final BukkitTime DATA_REGISTRY_WARMUP_DELAY = BukkitTime.ticks(6L);

    private final PlayerLanguage feature;

    public LanguageListener(PlayerLanguage feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        var player = e.getPlayer();
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    feature.getService().warm(player.getUniqueId(), player.getName());
                },
                DATA_REGISTRY_WARMUP_DELAY
        );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        feature.getService().forget(e.getPlayer().getUniqueId());
    }
}
