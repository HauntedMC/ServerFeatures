package nl.hauntedmc.serverfeatures.features.graveyard.runtime;

import nl.hauntedmc.serverfeatures.api.graveyard.GraveSnapshot;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.graveyard.Graveyard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Delivers localized warning and expiry messages to grave owners.
 */
public final class GraveExpiryNotifier {
    private final Graveyard feature;
    private final GraveManager manager;
    private final GraveExpiryNotificationTracker tracker = new GraveExpiryNotificationTracker();

    public GraveExpiryNotifier(Graveyard feature, GraveManager manager) {
        this.feature = feature;
        this.manager = manager;
    }

    public void start() {
        long intervalTicks = Math.max(1L, feature.getSettings().reconciliationTicks());
        feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                this::tick,
                BukkitTime.ticks(intervalTicks),
                BukkitTime.ticks(intervalTicks)
        );
    }

    private void tick() {
        List<GraveSnapshot> snapshots = manager.allRuntimeGraves();
        Set<UUID> observedGraves = new HashSet<>(snapshots.size());
        for (GraveSnapshot snapshot : snapshots) {
            observedGraves.add(snapshot.graveId());
            EnumSet<GraveExpiryNotificationTracker.Notification> notifications = tracker.observe(snapshot);
            for (GraveExpiryNotificationTracker.Notification notification : notifications) {
                deliver(snapshot, notification);
            }
        }
        tracker.retainOnly(observedGraves);
    }

    private void deliver(
            GraveSnapshot snapshot,
            GraveExpiryNotificationTracker.Notification notification
    ) {
        Player owner = Bukkit.getPlayer(snapshot.ownerUuid());
        if (owner == null || !owner.isOnline()) {
            return;
        }

        switch (notification) {
            case WARNING -> owner.sendMessage(feature.getLocalizationHandler()
                    .getMessage("graveyard.expiry_warning")
                    .with("grave_id", snapshot.shortId())
                    .with(
                            "seconds",
                            Long.toString(GraveExpiryNotificationTracker.WARNING_THRESHOLD_MILLIS / 1_000L)
                    )
                    .forAudience(owner)
                    .build());
            case EXPIRED -> owner.sendMessage(feature.getLocalizationHandler()
                    .getMessage("graveyard.expired")
                    .with("grave_id", snapshot.shortId())
                    .forAudience(owner)
                    .build());
        }
        tracker.markDelivered(snapshot.graveId(), notification);
    }
}
