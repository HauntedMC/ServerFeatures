package nl.hauntedmc.serverfeatures.features.invtools.listener;

import io.papermc.paper.event.connection.configuration.PlayerConnectionInitialConfigureEvent;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.invtools.InvTools;
import nl.hauntedmc.serverfeatures.features.invtools.migration.PlayerDataMigrationCoordinator;
import nl.hauntedmc.serverfeatures.features.invtools.service.InvToolsService;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvToolsMigrationLoginFenceTest {

    private static final Component MIGRATION_MESSAGE = Component.text("migration in progress");

    @Test
    void asyncPreLoginIsRejectedBeforeServiceTouchesPlayerdata() {
        UUID playerId = UUID.randomUUID();
        InvTools feature = mock(InvTools.class);
        PlayerDataMigrationCoordinator coordinator = mock(PlayerDataMigrationCoordinator.class);
        InvToolsService service = mock(InvToolsService.class);
        AsyncPlayerPreLoginEvent event = mock(AsyncPlayerPreLoginEvent.class);
        when(feature.getMigrationCoordinator()).thenReturn(coordinator);
        when(coordinator.blocksLogin(playerId)).thenReturn(true);
        when(event.getLoginResult()).thenReturn(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        when(event.getUniqueId()).thenReturn(playerId);
        when(event.getName()).thenReturn("HauntedMC");

        new InvToolsListener(feature, service, ignored -> MIGRATION_MESSAGE).onPreLogin(event);

        verify(event).disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                MIGRATION_MESSAGE
        );
        verify(service, never()).prepareLogin(playerId);
    }

    @Test
    void initialConfigurationRepeatsFenceBeforePlayerConstruction() {
        UUID playerId = UUID.randomUUID();
        InvTools feature = mock(InvTools.class);
        PlayerDataMigrationCoordinator coordinator = mock(PlayerDataMigrationCoordinator.class);
        InvToolsService service = mock(InvToolsService.class);
        PlayerConnectionInitialConfigureEvent event = mock(
                PlayerConnectionInitialConfigureEvent.class,
                RETURNS_DEEP_STUBS
        );
        when(feature.getMigrationCoordinator()).thenReturn(coordinator);
        when(coordinator.blocksLogin(playerId)).thenReturn(true);
        when(event.getConnection().getProfile().getId()).thenReturn(playerId);
        when(event.getConnection().getProfile().getName()).thenReturn("HauntedMC");

        new InvToolsListener(feature, service, ignored -> MIGRATION_MESSAGE)
                .onInitialConfigure(event);

        verify(event.getConnection()).disconnect(MIGRATION_MESSAGE);
        verify(service, never()).handlePlayerDataLoad(playerId);
    }

    @Test
    void nonMigratingInitialConfigurationReachesExistingPlayerdataGuard() {
        UUID playerId = UUID.randomUUID();
        InvTools feature = mock(InvTools.class);
        PlayerDataMigrationCoordinator coordinator = mock(PlayerDataMigrationCoordinator.class);
        InvToolsService service = mock(InvToolsService.class);
        PlayerConnectionInitialConfigureEvent event = mock(
                PlayerConnectionInitialConfigureEvent.class,
                RETURNS_DEEP_STUBS
        );
        when(feature.getMigrationCoordinator()).thenReturn(coordinator);
        when(coordinator.blocksLogin(playerId)).thenReturn(false);
        when(event.getConnection().getProfile().getId()).thenReturn(playerId);

        new InvToolsListener(feature, service, ignored -> MIGRATION_MESSAGE)
                .onInitialConfigure(event);

        verify(service).handlePlayerDataLoad(playerId);
        verify(event.getConnection(), never()).disconnect(any(Component.class));
    }

    @Test
    void nonMigratingPlayerContinuesThroughExistingLoginBarrier() {
        UUID playerId = UUID.randomUUID();
        InvTools feature = mock(InvTools.class);
        PlayerDataMigrationCoordinator coordinator = mock(PlayerDataMigrationCoordinator.class);
        InvToolsService service = mock(InvToolsService.class);
        AsyncPlayerPreLoginEvent event = mock(AsyncPlayerPreLoginEvent.class);
        when(feature.getMigrationCoordinator()).thenReturn(coordinator);
        when(coordinator.blocksLogin(playerId)).thenReturn(false);
        when(event.getLoginResult()).thenReturn(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        when(event.getUniqueId()).thenReturn(playerId);
        when(service.prepareLogin(playerId)).thenReturn(InvToolsService.LoginBarrierResult.ALLOW);

        new InvToolsListener(feature, service, ignored -> MIGRATION_MESSAGE).onPreLogin(event);

        verify(service).prepareLogin(playerId);
        verify(event, never()).disallow(
                any(AsyncPlayerPreLoginEvent.Result.class),
                any(Component.class)
        );
    }
}
