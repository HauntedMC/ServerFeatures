package nl.hauntedmc.serverfeatures.features.fairperks.service;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.fairperks.FairPerks;
import nl.hauntedmc.serverfeatures.features.fairperks.config.FairPerksSettings;
import nl.hauntedmc.serverfeatures.features.fairperks.migration.LegacyEssentialsStateMigrator;
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
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PerkStateServiceTest {

    @Test
    void initializationIsIdempotent() {
        Fixture fixture = fixture();

        fixture.service().initializeIfAbsent(fixture.player());
        fixture.service().initializeIfAbsent(fixture.player());

        verify(fixture.migrator(), times(1)).migrate(fixture.player());
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
        LegacyEssentialsStateMigrator migrator = mock(LegacyEssentialsStateMigrator.class);
        FairPerksSettings settings = settings();

        when(feature.getPlugin()).thenReturn(plugin);
        when(plugin.getServer()).thenReturn(server);
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getPersistentDataContainer()).thenReturn(data);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.hasPermission(FairPerks.FLY_USE_PERMISSION)).thenReturn(true);
        when(player.hasPermission(FairPerks.FLY_PERSIST_PERMISSION)).thenReturn(true);
        when(player.isFlying()).thenReturn(false);
        when(policy.allowsEnvironment(player, PerkType.FLY)).thenReturn(true);
        when(policy.canEnable(player, PerkType.FLY, true)).thenReturn(PerkChangeResult.Status.CHANGED);
        when(migrator.migrate(player)).thenReturn(
                LegacyEssentialsStateMigrator.MigrationResult.unavailable()
        );

        PerkStateService service = new PerkStateService(feature, settings, policy, migrator);
        return new Fixture(player, data, migrator, service);
    }

    private static FairPerksSettings settings() {
        FairPerksSettings.WorldRule worlds = new FairPerksSettings.WorldRule(
                FairPerksSettings.WorldMode.BLACKLIST,
                Set.of()
        );
        return new FairPerksSettings(
                new FairPerksSettings.CommandSettings(List.of(), List.of(), List.of()),
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
                new FairPerksSettings.ActivationGuardSettings(true, true, true, 16, 8),
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
                new FairPerksSettings.MigrationSettings(true, true, true, true)
        );
    }

    private record Fixture(
            Player player,
            PersistentDataContainer data,
            LegacyEssentialsStateMigrator migrator,
            PerkStateService service
    ) {
    }
}
