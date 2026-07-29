package nl.hauntedmc.serverfeatures.features.invtools.migration;

import nl.hauntedmc.serverfeatures.features.invtools.InvTools;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerDataMigrationCoordinatorTest {

    @Test
    void fencesFromIdentityResolutionUntilPlayerdataOperationFinishes() {
        MutableClock clock = new MutableClock();
        PlayerDataMigrationCoordinator coordinator = new PlayerDataMigrationCoordinator(
                mock(InvTools.class),
                clock
        );
        Player actor = mock(Player.class);
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(actor.getUniqueId()).thenReturn(actorId);

        coordinator.registerRequest(actor, "HauntedMC");
        assertFalse(coordinator.blocksLogin(targetId));

        coordinator.identityResolved("hauntedmc", Optional.of(targetId));
        assertTrue(coordinator.blocksLogin(targetId));

        coordinator.operationStarted(targetId);
        assertTrue(coordinator.blocksLogin(targetId));

        coordinator.operationFinished(targetId);
        assertFalse(coordinator.blocksLogin(targetId));
    }

    @Test
    void sameNameRequestsAreDrainedTogetherInsteadOfBeingReassignedOutOfOrder() {
        PlayerDataMigrationCoordinator coordinator = new PlayerDataMigrationCoordinator(
                mock(InvTools.class),
                new MutableClock()
        );
        Player firstActor = mock(Player.class);
        Player secondActor = mock(Player.class);
        when(firstActor.getUniqueId()).thenReturn(UUID.randomUUID());
        when(secondActor.getUniqueId()).thenReturn(UUID.randomUUID());
        UUID resolvedTarget = UUID.randomUUID();
        UUID unrelatedLaterResolution = UUID.randomUUID();

        coordinator.registerRequest(firstActor, "HauntedMC");
        coordinator.registerRequest(secondActor, "hauntedmc");
        coordinator.identityResolved("HAUNTEDMC", Optional.of(resolvedTarget));
        coordinator.identityResolved("HauntedMC", Optional.of(unrelatedLaterResolution));

        assertTrue(coordinator.blocksLogin(resolvedTarget));
        assertFalse(coordinator.blocksLogin(unrelatedLaterResolution));
    }

    @Test
    void nestedPlayerdataOperationsKeepFenceUntilLastOperationFinishes() {
        PlayerDataMigrationCoordinator coordinator = new PlayerDataMigrationCoordinator(
                mock(InvTools.class),
                new MutableClock()
        );
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
        PlayerDataMigrationCoordinator coordinator = new PlayerDataMigrationCoordinator(
                mock(InvTools.class),
                new MutableClock()
        );
        Player actor = mock(Player.class);
        when(actor.getUniqueId()).thenReturn(UUID.randomUUID());

        coordinator.registerRequest(actor, "MissingPlayer");
        coordinator.identityResolved("MissingPlayer", Optional.empty());

        assertFalse(coordinator.blocksLogin(UUID.randomUUID()));
    }

    @Test
    void abandonedIdentityHandoffFenceExpires() {
        MutableClock clock = new MutableClock();
        PlayerDataMigrationCoordinator coordinator = new PlayerDataMigrationCoordinator(
                mock(InvTools.class),
                clock
        );
        Player actor = mock(Player.class);
        UUID targetId = UUID.randomUUID();
        when(actor.getUniqueId()).thenReturn(UUID.randomUUID());

        coordinator.registerRequest(actor, "HauntedMC");
        coordinator.identityResolved("HauntedMC", Optional.of(targetId));
        assertTrue(coordinator.blocksLogin(targetId));

        clock.advanceSeconds(31);

        assertFalse(coordinator.blocksLogin(targetId));
    }

    @Test
    void shutdownReleasesAllMigrationFences() {
        PlayerDataMigrationCoordinator coordinator = new PlayerDataMigrationCoordinator(
                mock(InvTools.class),
                new MutableClock()
        );
        UUID playerId = UUID.randomUUID();
        coordinator.operationStarted(playerId);
        assertTrue(coordinator.blocksLogin(playerId));

        coordinator.shutdown();

        assertFalse(coordinator.blocksLogin(playerId));
    }

    private static final class MutableClock extends Clock {
        private Instant current = Instant.parse("2026-07-29T20:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advanceSeconds(long seconds) {
            current = current.plusSeconds(seconds);
        }
    }
}
