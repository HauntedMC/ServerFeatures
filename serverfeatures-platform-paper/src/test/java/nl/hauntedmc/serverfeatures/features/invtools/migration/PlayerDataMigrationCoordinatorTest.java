package nl.hauntedmc.serverfeatures.features.invtools.migration;

import nl.hauntedmc.serverfeatures.features.invtools.InvTools;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerDataMigrationCoordinatorTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-29T20:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void identityResolutionDoesNotCreateAStaleLoginFence() {
        PlayerDataMigrationCoordinator coordinator = coordinator();
        Player actor = mock(Player.class);
        UUID targetId = UUID.randomUUID();
        when(actor.getUniqueId()).thenReturn(UUID.randomUUID());

        coordinator.registerRequest(actor, "HauntedMC");
        coordinator.identityResolved("hauntedmc", Optional.of(targetId));

        assertFalse(coordinator.blocksLogin(targetId));
    }

    @Test
    void activePlayerdataOperationFencesUntilItFinishes() {
        PlayerDataMigrationCoordinator coordinator = coordinator();
        UUID targetId = UUID.randomUUID();

        coordinator.operationStarted(targetId);
        assertTrue(coordinator.blocksLogin(targetId));

        coordinator.operationFinished(targetId);
        assertFalse(coordinator.blocksLogin(targetId));
    }

    @Test
    void nestedPlayerdataOperationsKeepFenceUntilLastOperationFinishes() {
        PlayerDataMigrationCoordinator coordinator = coordinator();
        UUID playerId = UUID.randomUUID();

        coordinator.operationStarted(playerId);
        coordinator.operationStarted(playerId);
        coordinator.operationFinished(playerId);

        assertTrue(coordinator.blocksLogin(playerId));

        coordinator.operationFinished(playerId);

        assertFalse(coordinator.blocksLogin(playerId));
    }

    @Test
    void unresolvedIdentityNeverFencesAnUnrelatedUuid() {
        PlayerDataMigrationCoordinator coordinator = coordinator();
        Player actor = mock(Player.class);
        when(actor.getUniqueId()).thenReturn(UUID.randomUUID());

        coordinator.registerRequest(actor, "MissingPlayer");
        coordinator.identityResolved("MissingPlayer", Optional.empty());

        assertFalse(coordinator.blocksLogin(UUID.randomUUID()));
    }

    @Test
    void shutdownRunsStorageBarrierBeforeReleasingLoginFence() {
        PlayerDataMigrationCoordinator coordinator = coordinator();
        UUID playerId = UUID.randomUUID();
        AtomicBoolean barrierRan = new AtomicBoolean();
        coordinator.operationStarted(playerId);
        coordinator.attachShutdownBarrier(() -> {
            assertTrue(coordinator.blocksLogin(playerId));
            coordinator.operationFinished(playerId);
            barrierRan.set(true);
        });

        coordinator.shutdown();

        assertTrue(barrierRan.get());
        assertFalse(coordinator.blocksLogin(playerId));
    }

    @Test
    void shutdownBarrierCanOnlyBeAttachedOnce() {
        PlayerDataMigrationCoordinator coordinator = coordinator();
        coordinator.attachShutdownBarrier(() -> {
        });

        assertThrows(
                IllegalStateException.class,
                () -> coordinator.attachShutdownBarrier(() -> {
                })
        );
    }

    private static PlayerDataMigrationCoordinator coordinator() {
        return new PlayerDataMigrationCoordinator(mock(InvTools.class), FIXED_CLOCK);
    }
}
