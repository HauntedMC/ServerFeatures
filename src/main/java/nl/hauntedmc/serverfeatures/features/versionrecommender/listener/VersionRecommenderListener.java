package nl.hauntedmc.serverfeatures.features.versionrecommender.listener;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.versionrecommender.VersionRecommender;
import nl.hauntedmc.serverfeatures.features.versionrecommender.internal.RecommendationService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * On join, schedules a single recommendation after a (configurable) delay.
 * Reads and caches the delay in its constructor.
 */
public final class VersionRecommenderListener implements Listener {

    private final VersionRecommender feature;
    private final RecommendationService service;

    private final int delaySeconds;

    public VersionRecommenderListener(VersionRecommender feature, RecommendationService service) {
        this.feature = feature;
        this.service = service;
        int cfg = (int) feature.getConfigHandler().get("delay_seconds");
        this.delaySeconds = Math.max(0, cfg);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        feature.getLifecycleManager()
                .getTaskManager()
                .scheduleDelayedTask(
                        () -> {
                            if (player.isOnline()) service.recommendIfNeeded(player);
                        },
                        BukkitTime.seconds(delaySeconds)
                );
    }
}
