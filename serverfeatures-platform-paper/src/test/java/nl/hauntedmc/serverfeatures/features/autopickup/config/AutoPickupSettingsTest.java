package nl.hauntedmc.serverfeatures.features.autopickup.config;

import org.bukkit.SoundCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutoPickupSettingsTest {

    @Test
    void retryBackoffStopsAtConfiguredMaximum() {
        AutoPickupSettings.RetrySettings retry = new AutoPickupSettings.RetrySettings(5, 250L, 2000L);

        assertEquals(250L, retry.delayForAttempt(0));
        assertEquals(500L, retry.delayForAttempt(1));
        assertEquals(1000L, retry.delayForAttempt(2));
        assertEquals(2000L, retry.delayForAttempt(3));
        assertEquals(2000L, retry.delayForAttempt(30));
    }

    @Test
    void retrySettingsRejectPathologicalSchedulerValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AutoPickupSettings.RetrySettings(0, 250L, 2000L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AutoPickupSettings.RetrySettings(11, 250L, 2000L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AutoPickupSettings.RetrySettings(3, 60_001L, 60_001L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AutoPickupSettings.RetrySettings(3, 2000L, 1000L)
        );
    }

    @Test
    void notificationDurationIsBounded() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AutoPickupSettings.NotificationSettings(true, true, 0L, 61)
        );
    }

    @Test
    void pickupSoundVolumeIsBounded() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AutoPickupSettings.PickupSoundSettings(
                        true,
                        "minecraft:entity.item.pickup",
                        SoundCategory.PLAYERS,
                        16.1F,
                        1.0F
                )
        );
    }
}
