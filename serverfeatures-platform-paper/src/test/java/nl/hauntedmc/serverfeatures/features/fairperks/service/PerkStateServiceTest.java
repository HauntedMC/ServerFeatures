package nl.hauntedmc.serverfeatures.features.fairperks.service;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.fairperks.FairPerks;
import nl.hauntedmc.serverfeatures.features.fairperks.config.FairPerksSettings;
import nl.hauntedmc.serverfeatures.features.fairperks.model.PerkChangeResult;
import nl.hauntedmc.serverfeatures.features.fairperks.model.PerkType;
import nl.hauntedmc.serverfeatures.features.fairperks.policy.FairPerksPolicy;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PerkStateServiceTest {

    @Test
    void initializationIsIdempotent() {
        Fixture fixture = fixture();

        fixture.service().initializeIfAbsent(fixture.player());
        fixture.service().set(fixture.player(), PerkType.FLY, true, true);
        fixture.service().initializeIfAbsent(fixture.player());

        assertTrue(fixture.service().isDesired(fixture.player(), PerkType.FLY));
    }

    @Test
    void initializationDoesNotRevokeUnownedExternalFlight() {
        Fixture fixture = fixture();
        when(fixture.player().hasPermission(FairPerks.FLY_USE_PERMISSION)).thenReturn(false);
        when(fixture.player().hasPermission(FairPerks.FLY_PERSIST_PERMISSION)).thenReturn(false);
        when(fixture.player().getAllowFlight()).thenReturn(true);

        fixture.service().initialize(fixture.player());

        verify(fixture.player(), never()).setAllowFlight(false);
    }

    @Test
    void shutdownPersistsActiveFlightBeforeRevocation() {
        Fixture fixture = fixture();
        fixture.service().initialize(fixture.player());
        when(fixture.player().isFlying()).thenReturn(true);

        fixture.service().set(fixture.player(), PerkType.FLY, true, true);
        clearInvocations(fixture.data());
        fixture.service().cleanupForDisable();

        verify(fixture.data()).set(
                argThat(PerkStateServiceTest::isActiveFlightKey),
                eq(PersistentDataType.BYTE),
                eq((byte) 1)
        );
    }

    @Test
    void administrativeOverrideCanGrantSessionFlightWithoutTargetUsePermission() {
        Fixture fixture = fixture();
        fixture.service().initialize(fixture.player());
        when(fixture.player().hasPermission(FairPerks.FLY_USE_PERMISSION)).thenReturn(false);
        when(fixture.player().hasPermission(FairPerks.FLY_PERSIST_PERMISSION)).thenReturn(false);

        PerkChangeResult denied = fixture.service().set(
                fixture.player(),
                PerkType.FLY,
                true,
                true
        );
        assertEquals(PerkChangeResult.Status.NO_PERMISSION, denied.status());
        assertFalse(fixture.service().isDesired(fixture.player(), PerkType.FLY));

        PerkChangeResult granted = fixture.service().set(
                fixture.player(),
                PerkType.FLY,
                true,
                true,
                true
        );
        assertTrue(granted.success());
        assertTrue(fixture.service().isDesired(fixture.player(), PerkType.FLY));
    }

    @Test
    void reloadDoesNotRestoreFlightWhileCombatTagged() {
        Fixture fixture = fixture();
        when(fixture.policy().isCombatTagged(fixture.player())).thenReturn(true);
        UUID playerId = fixture.player().getUniqueId();

        fixture.service().restore(Map.of(
                playerId,
                new PerkStateService.PlayerSnapshot(
                        true,
                        false,
                        false,
                        true,
                        false,
                        false,
                        false
                )
        ));

        assertFalse(fixture.service().isDesired(fixture.player(), PerkType.FLY));
        verify(fixture.player()).setAllowFlight(false);
        verify(fixture.feature()).sendMessage(fixture.player(), "fairperks.fly.disabled");
        verify(fixture.data()).remove(
                argThat(key -> key != null && "fairperks_fly_enabled".equals(key.getKey()))
        );
    }

    @Test
    void combatCleanupDoesNotStopUnownedActiveFlight() {
        Fixture fixture = fixture();
        when(fixture.policy().isCombatTagged(fixture.player())).thenReturn(true);
        when(fixture.player().isFlying()).thenReturn(true);
        UUID playerId = fixture.player().getUniqueId();

        fixture.service().restore(Map.of(
                playerId,
                new PerkStateService.PlayerSnapshot(
                        true,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false
                )
        ));

        assertFalse(fixture.service().isDesired(fixture.player(), PerkType.FLY));
        verify(fixture.player(), never()).setFlying(false);
    }

    @Test
    void enteringGloballyBlockedWorldDisablesAllFairPerksState() {
        Fixture fixture = fixture();
        fixture.service().initialize(fixture.player());
        fixture.service().set(fixture.player(), PerkType.FLY, true, true);
        fixture.service().set(fixture.player(), PerkType.GOD, true, true);
        fixture.service().setGodMacro(fixture.player(), true);
        clearInvocations(fixture.player(), fixture.data());
        when(fixture.policy().allowsFairPerksWorld(fixture.player())).thenReturn(false);

        fixture.service().reconcileEnvironment(fixture.player());

        assertFalse(fixture.service().isDesired(fixture.player(), PerkType.FLY));
        assertFalse(fixture.service().isDesired(fixture.player(), PerkType.GOD));
        assertFalse(fixture.service().isGodMacroEnabled(fixture.player()));
        verify(fixture.player()).setAllowFlight(false);
        verify(fixture.data()).remove(
                argThat(key -> key != null && "fairperks_fly_enabled".equals(key.getKey()))
        );
        verify(fixture.data()).remove(
                argThat(key -> key != null && "fairperks_god_enabled".equals(key.getKey()))
        );
        verify(fixture.data()).remove(
                argThat(key -> key != null && "fairperks_god_macro".equals(key.getKey()))
        );

        when(fixture.policy().allowsFairPerksWorld(fixture.player())).thenReturn(true);
        fixture.service().reconcileEnvironment(fixture.player());

        assertFalse(fixture.service().isDesired(fixture.player(), PerkType.FLY));
        assertFalse(fixture.service().isDesired(fixture.player(), PerkType.GOD));
        assertFalse(fixture.service().isGodMacroEnabled(fixture.player()));
    }

    @Test
    void globallyBlockedWorldRejectsEnablingGodMacro() {
        Fixture fixture = fixture();
        fixture.service().initialize(fixture.player());
        when(fixture.policy().allowsFairPerksWorld(fixture.player())).thenReturn(false);

        PerkChangeResult result = fixture.service().setGodMacro(fixture.player(), true);

        assertEquals(PerkChangeResult.Status.WORLD_BLOCKED, result.status());
        assertFalse(fixture.service().isGodMacroEnabled(fixture.player()));
    }

    private static boolean isActiveFlightKey(NamespacedKey key) {
        return key != null && "fairperks_fly_active".equals(key.getKey());
    }

    private static Fixture fixture() {
        FairPerks feature = mock(FairPerks.class);
        ServerFeatures plugin = mock(ServerFeatures.class);
        Server server = mock(Server.class);
        Player player = mock(Player.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        FairPerksPolicy policy = mock(FairPerksPolicy.class);
        FairPerksSettings settings = settings();

        when(feature.getPlugin()).thenReturn(plugin);
        when(plugin.getServer()).thenReturn(server);
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getPersistentDataContainer()).thenReturn(data);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.hasPermission(FairPerks.FLY_USE_PERMISSION)).thenReturn(true);
        when(player.hasPermission(FairPerks.FLY_PERSIST_PERMISSION)).thenReturn(true);
        when(player.hasPermission(FairPerks.GOD_USE_PERMISSION)).thenReturn(true);
        when(player.hasPermission(FairPerks.GOD_PERSIST_PERMISSION)).thenReturn(true);
        when(player.hasPermission(FairPerks.GOD_MACRO_PERMISSION)).thenReturn(true);
        when(player.isFlying()).thenReturn(false);
        when(policy.isCombatTagged(player)).thenReturn(false);
        when(policy.allowsFairPerksWorld(player)).thenReturn(true);
        when(policy.allowsEnvironment(player, PerkType.FLY)).thenReturn(true);
        when(policy.canEnable(player, PerkType.FLY, true)).thenReturn(
                PerkChangeResult.Status.CHANGED
        );
        when(policy.canEnable(player, PerkType.GOD, true)).thenReturn(
                PerkChangeResult.Status.CHANGED
        );
        PerkStateService service = new PerkStateService(feature, settings, policy);
        return new Fixture(feature, player, data, policy, service);
    }

    private static FairPerksSettings settings() {
        FairPerksSettings.WorldRule worlds = new FairPerksSettings.WorldRule(
                FairPerksSettings.WorldMode.BLACKLIST,
                Set.of()
        );
        return new FairPerksSettings(
                new FairPerksSettings.CommandSettings(List.of(), List.of(), List.of()),
                new FairPerksSettings.WorldRule(FairPerksSettings.WorldMode.ALL, Set.of()),
                new FairPerksSettings.FlightSettings(
                        true,
                        Set.of(GameMode.SURVIVAL),
                        worlds,
                        true,
                        true,
                        true,
                        false
                ),
                new FairPerksSettings.GodSettings(
                        Set.of(GameMode.SURVIVAL),
                        worlds,
                        true,
                        false
                ),
                new FairPerksSettings.ActivationGuardSettings(true, true, 16, 8),
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
                        true,
                        5,
                        5,
                        10,
                        Set.of()
                ),
                new FairPerksSettings.HostileSettings(Set.of(), Set.of(), true, false),
                new FairPerksSettings.GodMacroSettings(true, 350L),
                new FairPerksSettings.FeedbackSettings(1_000_000_000L)
        );
    }

    private record Fixture(
            FairPerks feature,
            Player player,
            PersistentDataContainer data,
            FairPerksPolicy policy,
            PerkStateService service
    ) {
    }
}
