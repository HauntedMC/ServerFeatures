package nl.hauntedmc.serverfeatures.framework.persistence;

import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerIdentityResolverTest {

    @Test
    void returnsTheCachedPublicIdentityWithoutQueryingPersistence() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        UUID uuid = UUID.randomUUID();
        PlayerIdentity identity = new PlayerIdentity(12L, uuid, "Alice");
        when(directory.findActiveIdentityCached(uuid)).thenReturn(Optional.of(identity));

        PlayerIdentity result = new PlayerIdentityResolver(directory).findByUuid(uuid)
                .toCompletableFuture()
                .join()
                .orElseThrow();

        assertEquals(identity, result);
        verify(directory, never()).findByUuid(uuid);
    }

    @Test
    void resolvesPersistedIdentityByUuidWhenPlayerIsOffline() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        UUID uuid = UUID.randomUUID();
        PlayerIdentity identity = new PlayerIdentity(13L, uuid, "OfflineAlice");
        when(directory.findActiveIdentityCached(uuid)).thenReturn(Optional.empty());
        when(directory.findByUuid(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.of(identity)));

        PlayerIdentity result = new PlayerIdentityResolver(directory).findByUuid(uuid)
                .toCompletableFuture()
                .join()
                .orElseThrow();

        assertEquals(identity, result);
        verify(directory).findByUuid(uuid);
    }

    @Test
    void resolvesPersistedUsernameIgnoringCaseWhenPlayerIsOffline() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        PlayerIdentity identity = new PlayerIdentity(43L, UUID.randomUUID(), "Alice");
        when(directory.snapshotActiveIdentities()).thenReturn(Map.of());
        when(directory.findByUsernameIgnoreCase("aLiCe"))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(identity)));

        PlayerIdentity result = new PlayerIdentityResolver(directory).findByUsername("aLiCe")
                .toCompletableFuture()
                .join()
                .orElseThrow();

        assertEquals(identity, result);
        verify(directory).findByUsernameIgnoreCase("aLiCe");
    }

    @Test
    void normalizesPersistedUsernameInput() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        PlayerIdentity identity = new PlayerIdentity(44L, UUID.randomUUID(), "Alice");
        when(directory.snapshotActiveIdentities()).thenReturn(Map.of());
        when(directory.findByUsernameIgnoreCase("Alice"))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(identity)));

        PlayerIdentity result = new PlayerIdentityResolver(directory).findByUsername("  Alice  ")
                .toCompletableFuture()
                .join()
                .orElseThrow();

        assertEquals(identity, result);
        verify(directory).findByUsernameIgnoreCase("Alice");
    }

    @Test
    void returnsEmptyWhenPersistedIdentityDoesNotExist() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        UUID uuid = UUID.randomUUID();
        when(directory.findActiveIdentityCached(uuid)).thenReturn(Optional.empty());
        when(directory.findByUuid(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        assertTrue(new PlayerIdentityResolver(directory).findByUuid(uuid)
                .toCompletableFuture()
                .join()
                .isEmpty());
    }
}
