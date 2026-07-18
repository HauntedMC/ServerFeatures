package nl.hauntedmc.serverfeatures.features.glow.listener;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.glow.Glow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener to restore glow on join and remove transiently on quit.
 */
public class GlowListener implements Listener {

    private static final BukkitTime DATA_REGISTRY_WARMUP_DELAY = BukkitTime.ticks(6L);

    private final Glow feature;

    public GlowListener(Glow feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        // Load persisted state and, if enabled & valid, restore the glow.
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                () -> {
                    if (!p.isOnline()) {
                        return;
                    }
                    feature.getGlowStateService().restoreGlowFor(p);
                },
                DATA_REGISTRY_WARMUP_DELAY
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Remove from scoreboard / memory only; DO NOT persist disable on quit.
        feature.getGlowHandler().removeGlowTransient(player);
    }
}
