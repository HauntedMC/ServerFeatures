package nl.hauntedmc.serverfeatures.features.fairperks.config;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FairPerksSettingsTest {

    @Test
    void blacklistWorldRuleAllowsUnlistedWorlds() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("survival");

        FairPerksSettings.WorldRule rule = new FairPerksSettings.WorldRule(
                FairPerksSettings.WorldMode.BLACKLIST,
                Set.of("resource")
        );

        assertTrue(rule.allows(world));
    }

    @Test
    void whitelistWorldRuleRejectsUnlistedWorlds() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("survival");

        FairPerksSettings.WorldRule rule = new FairPerksSettings.WorldRule(
                FairPerksSettings.WorldMode.WHITELIST,
                Set.of("resource")
        );

        assertFalse(rule.allows(world));
    }

    @Test
    void worldRuleNormalizesConstructorValues() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("Resource");

        FairPerksSettings.WorldRule rule = new FairPerksSettings.WorldRule(
                FairPerksSettings.WorldMode.WHITELIST,
                Set.of(" RESOURCE ")
        );

        assertTrue(rule.allows(world));
    }

    @Test
    void flightSettingsRequireBothAllowedGameModeAndWorld() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("survival");

        org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getWorld()).thenReturn(world);

        FairPerksSettings.FlightSettings settings = new FairPerksSettings.FlightSettings(
                true,
                Set.of(GameMode.SURVIVAL),
                new FairPerksSettings.WorldRule(
                        FairPerksSettings.WorldMode.BLACKLIST,
                        Set.of("resource")
                ),
                true,
                true,
                true,
                true
        );

        assertTrue(settings.allows(player));
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        assertFalse(settings.allows(player));
    }

    @Test
    void commandSettingsAreDeeplyImmutableAndNormalized() {
        List<String> aliases = new java.util.ArrayList<>(List.of(" Flight "));
        FairPerksSettings.CommandSettings settings = new FairPerksSettings.CommandSettings(
                aliases,
                List.of(),
                List.of()
        );

        aliases.clear();

        assertEquals(List.of("flight"), settings.flyAliases());
        assertThrows(UnsupportedOperationException.class, () -> settings.flyAliases().add("other"));
    }

    @Test
    void commandSettingsRejectInvalidAliases() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FairPerksSettings.CommandSettings(
                        List.of("not a command"),
                        List.of(),
                        List.of()
                )
        );
    }
}
