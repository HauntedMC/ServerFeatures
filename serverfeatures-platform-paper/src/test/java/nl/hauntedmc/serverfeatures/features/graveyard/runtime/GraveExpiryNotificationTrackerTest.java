package nl.hauntedmc.serverfeatures.features.graveyard.runtime;

import nl.hauntedmc.serverfeatures.api.graveyard.GraveSnapshot;
import nl.hauntedmc.serverfeatures.api.graveyard.GraveStatus;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraveExpiryNotificationTrackerTest {
    @Test
    void warningRemainsPendingUntilItIsDelivered() {
        GraveExpiryNotificationTracker tracker = new GraveExpiryNotificationTracker();
        UUID graveId = UUID.randomUUID();

        assertTrue(tracker.observe(snapshot(graveId, GraveStatus.ACTIVE, 60_001L)).isEmpty());
        assertEquals(
                EnumSet.of(GraveExpiryNotificationTracker.Notification.WARNING),
                tracker.observe(snapshot(graveId, GraveStatus.ACTIVE, 60_000L))
        );
        assertEquals(
                EnumSet.of(GraveExpiryNotificationTracker.Notification.WARNING),
                tracker.observe(snapshot(graveId, GraveStatus.ACTIVE, 59_000L))
        );

        tracker.markDelivered(graveId, GraveExpiryNotificationTracker.Notification.WARNING);

        assertTrue(tracker.observe(snapshot(graveId, GraveStatus.ACTIVE, 58_000L)).isEmpty());
    }

    @Test
    void expiryRemainsPendingUntilItIsDelivered() {
        GraveExpiryNotificationTracker tracker = new GraveExpiryNotificationTracker();
        UUID graveId = UUID.randomUUID();

        tracker.observe(snapshot(graveId, GraveStatus.ACTIVE, 61_000L));
        assertEquals(
                EnumSet.of(GraveExpiryNotificationTracker.Notification.EXPIRED),
                tracker.observe(snapshot(graveId, GraveStatus.EXPIRED, 0L))
        );
        assertEquals(
                EnumSet.of(GraveExpiryNotificationTracker.Notification.EXPIRED),
                tracker.observe(snapshot(graveId, GraveStatus.EXPIRED, 0L))
        );

        tracker.markDelivered(graveId, GraveExpiryNotificationTracker.Notification.EXPIRED);

        assertTrue(tracker.observe(snapshot(graveId, GraveStatus.EXPIRED, 0L)).isEmpty());
    }

    @Test
    void alreadyExpiredRecordsDoNotProduceStaleNotifications() {
        GraveExpiryNotificationTracker tracker = new GraveExpiryNotificationTracker();
        UUID graveId = UUID.randomUUID();

        assertTrue(tracker.observe(snapshot(graveId, GraveStatus.EXPIRED, 0L)).isEmpty());
    }

    @Test
    void restoredGraveStartsANewNotificationCycle() {
        GraveExpiryNotificationTracker tracker = new GraveExpiryNotificationTracker();
        UUID graveId = UUID.randomUUID();

        assertEquals(
                EnumSet.of(GraveExpiryNotificationTracker.Notification.WARNING),
                tracker.observe(snapshot(graveId, GraveStatus.ACTIVE, 30_000L))
        );
        tracker.markDelivered(graveId, GraveExpiryNotificationTracker.Notification.WARNING);
        assertEquals(
                EnumSet.of(GraveExpiryNotificationTracker.Notification.EXPIRED),
                tracker.observe(snapshot(graveId, GraveStatus.EXPIRED, 0L))
        );
        tracker.markDelivered(graveId, GraveExpiryNotificationTracker.Notification.EXPIRED);

        assertEquals(
                EnumSet.of(GraveExpiryNotificationTracker.Notification.WARNING),
                tracker.observe(snapshot(graveId, GraveStatus.ACTIVE, 30_000L))
        );
    }

    private static GraveSnapshot snapshot(UUID graveId, GraveStatus status, long remainingMillis) {
        return new GraveSnapshot(
                graveId,
                "Player-12:34:56",
                UUID.randomUUID(),
                "Player",
                "survival",
                "survival",
                UUID.randomUUID(),
                "minecraft:overworld",
                0.0,
                64.0,
                0.0,
                status,
                false,
                remainingMillis,
                1,
                0
        );
    }
}
