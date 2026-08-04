package nl.hauntedmc.serverfeatures.features.graveyard.runtime;

import nl.hauntedmc.serverfeatures.api.graveyard.GraveSnapshot;
import nl.hauntedmc.serverfeatures.api.graveyard.GraveStatus;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks one-shot player notifications across Graveyard lifecycle transitions.
 */
final class GraveExpiryNotificationTracker {
    static final long WARNING_THRESHOLD_MILLIS = 60_000L;

    private final Map<UUID, GraveStatus> previousStatuses = new HashMap<>();
    private final Set<UUID> deliveredWarnings = new HashSet<>();
    private final Set<UUID> pendingExpiryNotifications = new HashSet<>();

    EnumSet<Notification> observe(GraveSnapshot snapshot) {
        UUID graveId = snapshot.graveId();
        GraveStatus currentStatus = snapshot.status();
        GraveStatus previousStatus = previousStatuses.put(graveId, currentStatus);

        if (previousStatus == GraveStatus.EXPIRED && currentStatus != GraveStatus.EXPIRED) {
            deliveredWarnings.remove(graveId);
            pendingExpiryNotifications.remove(graveId);
        }
        if (currentStatus == GraveStatus.EXPIRED && isExpirable(previousStatus)) {
            pendingExpiryNotifications.add(graveId);
        }
        if (isTerminalWithoutExpiryNotification(currentStatus)) {
            deliveredWarnings.remove(graveId);
            pendingExpiryNotifications.remove(graveId);
        }

        EnumSet<Notification> notifications = EnumSet.noneOf(Notification.class);
        if (isExpirable(currentStatus)
                && snapshot.remainingActiveMillis() > 0L
                && snapshot.remainingActiveMillis() <= WARNING_THRESHOLD_MILLIS
                && !deliveredWarnings.contains(graveId)) {
            notifications.add(Notification.WARNING);
        }
        if (currentStatus == GraveStatus.EXPIRED && pendingExpiryNotifications.contains(graveId)) {
            notifications.add(Notification.EXPIRED);
        }
        return notifications;
    }

    void markDelivered(UUID graveId, Notification notification) {
        switch (notification) {
            case WARNING -> deliveredWarnings.add(graveId);
            case EXPIRED -> pendingExpiryNotifications.remove(graveId);
        }
    }

    void retainOnly(Set<UUID> graveIds) {
        previousStatuses.keySet().retainAll(graveIds);
        deliveredWarnings.retainAll(graveIds);
        pendingExpiryNotifications.retainAll(graveIds);
    }

    private static boolean isExpirable(GraveStatus status) {
        return status == GraveStatus.ACTIVE || status == GraveStatus.PARTIAL;
    }

    private static boolean isTerminalWithoutExpiryNotification(GraveStatus status) {
        return status == GraveStatus.CLAIMED
                || status == GraveStatus.CORRUPT
                || status == GraveStatus.ADMIN_RECOVERED
                || status == GraveStatus.PURGED;
    }

    enum Notification {
        WARNING,
        EXPIRED
    }
}
