package nl.hauntedmc.serverfeatures.features.combattag.config;

import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CombatTagSettingsTest {

    @Test
    void tagModeSeparatesPvpAndMobCombat() {
        CombatTagSettings.TaggingSettings pvp = new CombatTagSettings.TaggingSettings(
                CombatTagSettings.TagMode.PVP,
                15,
                false,
                allWorlds()
        );
        CombatTagSettings.TaggingSettings mobs = new CombatTagSettings.TaggingSettings(
                CombatTagSettings.TagMode.MOBS,
                15,
                false,
                allWorlds()
        );

        assertTrue(pvp.pvpEnabled());
        assertFalse(pvp.mobsEnabled());
        assertFalse(mobs.pvpEnabled());
        assertTrue(mobs.mobsEnabled());
    }

    @Test
    void worldRulesAreNormalizedAndImmutable() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("Resource");
        Set<String> configured = new java.util.HashSet<>(Set.of(" RESOURCE "));

        CombatTagSettings.WorldRule rule = new CombatTagSettings.WorldRule(
                CombatTagSettings.WorldMode.WHITELIST,
                configured
        );
        configured.clear();

        assertTrue(rule.allows(world));
        assertThrows(UnsupportedOperationException.class, () -> rule.values().add("other"));
    }

    @Test
    void nestedSettingsDefensivelyCopyCollections() {
        List<String> commands = new ArrayList<>(List.of("say logged out"));
        CombatTagSettings settings = new CombatTagSettings(
                new CombatTagSettings.TaggingSettings(
                        CombatTagSettings.TagMode.BOTH,
                        15,
                        false,
                        allWorlds()
                ),
                new CombatTagSettings.AttributionSettings(
                        true,
                        new CombatTagSettings.ProjectileSettings(
                                true,
                                Set.of(EntityType.EGG)
                        ),
                        true,
                        true,
                        Set.of(CreatureSpawnEvent.SpawnReason.SPAWNER)
                ),
                new CombatTagSettings.LifecycleSettings(true, true),
                new CombatTagSettings.TeleportSettings(
                        true,
                        true,
                        Set.of(PlayerTeleportEvent.TeleportCause.ENDER_PEARL),
                        false,
                        false
                ),
                new CombatTagSettings.LogoutSettings(true, true, true, false, commands),
                new CombatTagSettings.DisplaySettings(
                        true,
                        true,
                        new CombatTagSettings.ActionBarSettings(true, 5, 20, "█", "█")
                ),
                new CombatTagSettings.FeedbackSettings(1_000_000_000L)
        );
        commands.clear();

        assertFalse(settings.logout().punishKickedPlayers());
        assertTrue(settings.logout().commands().contains("say logged out"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> settings.logout().commands().add("other")
        );
    }

    private static CombatTagSettings.WorldRule allWorlds() {
        return new CombatTagSettings.WorldRule(CombatTagSettings.WorldMode.ALL, Set.of());
    }
}
