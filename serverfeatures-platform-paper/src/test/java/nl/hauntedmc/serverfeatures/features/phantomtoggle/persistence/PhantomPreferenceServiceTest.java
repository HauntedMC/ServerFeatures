package nl.hauntedmc.serverfeatures.features.phantomtoggle.persistence;

import nl.hauntedmc.serverfeatures.features.phantomtoggle.PhantomToggle;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhantomPreferenceServiceTest {

    @Test
    void missingPreferenceUsesConfiguredDefault() {
        Fixture fixture = fixture(null, false, true);

        assertFalse(fixture.service().phantomsEnabled(fixture.player()));
    }

    @Test
    void storedPreferenceOverridesConfiguredDefault() {
        Fixture fixture = fixture((byte) 0, true, true);

        assertFalse(fixture.service().phantomsEnabled(fixture.player()));
    }

    @Test
    void playerWithoutPermissionKeepsVanillaPhantomBehavior() {
        Fixture fixture = fixture((byte) 0, false, false);

        assertTrue(fixture.service().phantomsEnabled(fixture.player()));
        assertFalse(fixture.service().shouldSuppressSpawn(fixture.player()));
    }

    @Test
    void writesExplicitPreferenceToPlayerPdc() {
        Fixture fixture = fixture(null, true, true);

        fixture.service().setPhantomsEnabled(fixture.player(), false);

        verify(fixture.data()).set(
                PhantomPreferenceService.PHANTOMS_ENABLED_KEY,
                PersistentDataType.BYTE,
                (byte) 0
        );
    }

    @Test
    void invalidStoredValueIsDiscarded() {
        Fixture fixture = fixture((byte) 7, true, true);

        assertTrue(fixture.service().phantomsEnabled(fixture.player()));
        verify(fixture.data()).remove(PhantomPreferenceService.PHANTOMS_ENABLED_KEY);
    }

    private static Fixture fixture(Byte stored, boolean defaultEnabled, boolean hasPermission) {
        Player player = mock(Player.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(player.getPersistentDataContainer()).thenReturn(data);
        when(player.hasPermission(PhantomToggle.USE_PERMISSION)).thenReturn(hasPermission);
        when(data.get(PhantomPreferenceService.PHANTOMS_ENABLED_KEY, PersistentDataType.BYTE))
                .thenReturn(stored);

        return new Fixture(new PhantomPreferenceService(defaultEnabled), player, data);
    }

    private record Fixture(
            PhantomPreferenceService service,
            Player player,
            PersistentDataContainer data
    ) {
    }
}
