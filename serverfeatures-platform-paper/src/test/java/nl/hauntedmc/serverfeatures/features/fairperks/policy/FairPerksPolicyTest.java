package nl.hauntedmc.serverfeatures.features.fairperks.policy;

import nl.hauntedmc.serverfeatures.features.fairperks.config.FairPerksSettings;
import nl.hauntedmc.serverfeatures.features.fairperks.model.PerkChangeResult;
import nl.hauntedmc.serverfeatures.features.fairperks.model.PerkType;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FairPerksPolicyTest {

    @Test
    void environmentRulesStillApplyWhenActivationGuardIsBypassed() {
        Player player = player(GameMode.CREATIVE, "survival");
        FairPerksPolicy policy = policy(
                settings(true, true),
                ignored -> CombatStatusProvider.CombatStatus.IN_COMBAT
        );

        assertEquals(
                PerkChangeResult.Status.GAME_MODE_BLOCKED,
                policy.canEnable(player, PerkType.FLY, true)
        );
    }

    @Test
    void combatTagBlocksEnabling() {
        Player player = player(GameMode.SURVIVAL, "survival");
        FairPerksPolicy policy = policy(
                settings(true, true),
                ignored -> CombatStatusProvider.CombatStatus.IN_COMBAT
        );

        assertEquals(
                PerkChangeResult.Status.COMBAT_TAGGED,
                policy.canEnable(player, PerkType.GOD, false)
        );
    }

    @Test
    void unavailableCombatProviderUsesConfiguredDenyFallback() {
        Player player = player(GameMode.SURVIVAL, "survival");
        FairPerksPolicy policy = policy(
                settings(true, false),
                ignored -> CombatStatusProvider.CombatStatus.UNAVAILABLE
        );

        assertEquals(
                PerkChangeResult.Status.COMBAT_TAGGED,
                policy.canEnable(player, PerkType.FLY, false)
        );
    }

    @Test
    void nearbyHostileBlocksEnablingAfterCombatCheck() {
        Player player = player(GameMode.SURVIVAL, "survival");
        Enemy enemy = mock(Enemy.class);
        when(enemy.getType()).thenReturn(EntityType.ZOMBIE);
        when(player.getNearbyEntities(16.0D, 16.0D, 16.0D)).thenReturn(List.of(enemy));
        FairPerksPolicy policy = policy(
                settings(false, true),
                ignored -> CombatStatusProvider.CombatStatus.NOT_IN_COMBAT
        );

        assertEquals(
                PerkChangeResult.Status.HOSTILE_NEARBY,
                policy.canEnable(player, PerkType.FLY, false)
        );
    }

    @Test
    void bypassSkipsCombatAndHostileChecks() {
        Player player = player(GameMode.SURVIVAL, "survival");
        FairPerksPolicy policy = policy(
                settings(true, false),
                ignored -> CombatStatusProvider.CombatStatus.IN_COMBAT
        );

        assertEquals(
                PerkChangeResult.Status.CHANGED,
                policy.canEnable(player, PerkType.GOD, true)
        );
    }

    private static FairPerksPolicy policy(
            FairPerksSettings settings,
            CombatStatusProvider combatStatusProvider
    ) {
        return new FairPerksPolicy(
                settings,
                new HostileEntityClassifier(settings.hostiles()),
                combatStatusProvider
        );
    }

    private static Player player(GameMode gameMode, String worldName) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        Player player = mock(Player.class);
        when(player.getGameMode()).thenReturn(gameMode);
        when(player.getWorld()).thenReturn(world);
        when(player.getNearbyEntities(16.0D, 16.0D, 16.0D)).thenReturn(List.of());
        return player;
    }

    private static FairPerksSettings settings(
            boolean combatEnabled,
            boolean allowWhenCombatUnavailable
    ) {
        FairPerksSettings.WorldRule worlds = new FairPerksSettings.WorldRule(
                FairPerksSettings.WorldMode.BLACKLIST,
                Set.of()
        );
        return new FairPerksSettings(
                new FairPerksSettings.CommandSettings(List.of(), List.of(), List.of()),
                new FairPerksSettings.FlightSettings(
                        true,
                        Set.of(GameMode.SURVIVAL, GameMode.ADVENTURE),
                        worlds,
                        true,
                        true,
                        true,
                        true
                ),
                new FairPerksSettings.GodSettings(
                        Set.of(GameMode.SURVIVAL, GameMode.ADVENTURE),
                        worlds,
                        true,
                        false
                ),
                new FairPerksSettings.ActivationGuardSettings(
                        combatEnabled,
                        allowWhenCombatUnavailable,
                        true,
                        16,
                        16
                ),
                new FairPerksSettings.RestrictionSettings(
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        5,
                        5,
                        10,
                        Set.of()
                ),
                new FairPerksSettings.HostileSettings(Set.of(), Set.of(), true, false),
                new FairPerksSettings.GodMacroSettings(true, 350L),
                new FairPerksSettings.FeedbackSettings(1_000_000_000L),
                new FairPerksSettings.MigrationSettings(true, true)
        );
    }
}
