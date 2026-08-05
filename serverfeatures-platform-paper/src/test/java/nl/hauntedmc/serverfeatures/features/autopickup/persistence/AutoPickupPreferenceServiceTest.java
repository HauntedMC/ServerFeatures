package nl.hauntedmc.serverfeatures.features.autopickup.persistence;

import nl.hauntedmc.serverfeatures.features.autopickup.AutoPickup;
import nl.hauntedmc.serverfeatures.features.autopickup.config.AutoPickupSettings;
import nl.hauntedmc.serverfeatures.features.autopickup.model.AutoPickupPlayerState.CommandIntent;
import nl.hauntedmc.serverfeatures.features.autopickup.model.DropScope;
import org.bukkit.GameMode;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoPickupPreferenceServiceTest {

    @Test
    void missingPreferenceUsesConfiguredDefault() {
        Fixture fixture = fixture(null, true);

        fixture.service().initialize(fixture.player());

        assertTrue(fixture.service().isEnabled(fixture.player()));
    }

    @Test
    void storedPreferenceOverridesConfiguredDefault() {
        Fixture fixture = fixture((byte) 0, true);

        fixture.service().initialize(fixture.player());

        assertFalse(fixture.service().isEnabled(fixture.player()));
    }

    @Test
    void storedEnabledPreferenceOverridesDisabledDefault() {
        Fixture fixture = fixture((byte) 1, false);

        fixture.service().initialize(fixture.player());

        assertTrue(fixture.service().isEnabled(fixture.player()));
    }

    @Test
    void commandWritesExplicitFalseToPlayerPdc() {
        Fixture fixture = fixture(null, true);
        fixture.service().initialize(fixture.player());

        fixture.service().handleCommand(fixture.player(), CommandIntent.DISABLE);

        verify(fixture.data()).set(
                AutoPickupPreferenceService.ENABLED_KEY,
                PersistentDataType.BYTE,
                (byte) 0
        );
        assertFalse(fixture.service().isEnabled(fixture.player()));
    }

    @Test
    void safetyDisableDoesNotOverwritePersistedChoice() {
        Fixture fixture = fixture((byte) 1, false);
        fixture.service().initialize(fixture.player());

        fixture.service().disableForSession(fixture.player());

        assertFalse(fixture.service().isEnabled(fixture.player()));
        verify(fixture.data(), never()).set(
                eq(AutoPickupPreferenceService.ENABLED_KEY),
                eq(PersistentDataType.BYTE),
                eq((byte) 0)
        );
    }

    private static Fixture fixture(Byte stored, boolean defaultEnabled) {
        AutoPickup feature = mock(AutoPickup.class);
        Player player = mock(Player.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getPersistentDataContainer()).thenReturn(data);
        when(player.hasPermission(AutoPickup.USE_PERMISSION)).thenReturn(true);
        when(data.get(AutoPickupPreferenceService.ENABLED_KEY, PersistentDataType.BYTE))
                .thenReturn(stored);

        AutoPickupSettings settings = new AutoPickupSettings(
                defaultEnabled,
                DropScope.STRICT_DIRECT,
                AutoPickupSettings.WorldMode.BLACKLIST,
                Set.of(),
                Set.of(GameMode.SURVIVAL),
                true,
                new AutoPickupSettings.NotificationSettings(true, true, 3_000_000_000L, 2),
                new AutoPickupSettings.PickupSoundSettings(
                        true,
                        "minecraft:entity.item.pickup",
                        SoundCategory.PLAYERS,
                        0.2F,
                        1.0F
                ),
                30_000_000_000L
        );
        return new Fixture(
                new AutoPickupPreferenceService(feature, settings),
                player,
                data
        );
    }

    private record Fixture(
            AutoPickupPreferenceService service,
            Player player,
            PersistentDataContainer data
    ) {
    }
}
