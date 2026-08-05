package nl.hauntedmc.serverfeatures.features.autopickup.config;

import org.bukkit.SoundCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AutoPickupSettingsTest {

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
