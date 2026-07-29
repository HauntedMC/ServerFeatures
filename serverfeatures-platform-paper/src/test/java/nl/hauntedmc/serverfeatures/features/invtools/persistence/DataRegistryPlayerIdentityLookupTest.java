package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataRegistryPlayerIdentityLookupTest {

    @Test
    void resolvesPersistedOfflineIdentityWhenNoActiveEntryExists() throws IOException {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        PlayerIdentity identity = identity("HauntedMC");
        when(directory.snapshotActiveIdentities()).thenReturn(Map.of());
        when(directory.findByIdentifier("HauntedMC"))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(identity)));

        DataRegistryPlayerIdentityLookup lookup = new DataRegistryPlayerIdentityLookup(directory);

        assertEquals(
                Optional.of(new CanonicalPlayerIdentityLookup.Identity(
                        identity.uuid(),
                        identity.username()
                )),
                lookup.find("HauntedMC")
        );
    }

    @Test
    void usesTheActiveIdentityCacheWithoutQueryingPersistence() throws IOException {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        PlayerIdentity identity = identity("HauntedMC");
        when(directory.snapshotActiveIdentities()).thenReturn(Map.of(
                identity.uuid().toString(),
                identity
        ));

        DataRegistryPlayerIdentityLookup lookup = new DataRegistryPlayerIdentityLookup(directory);

        assertEquals(
                Optional.of(new CanonicalPlayerIdentityLookup.Identity(
                        identity.uuid(),
                        identity.username()
                )),
                lookup.find("hauntedmc")
        );
        verify(directory, never()).findByIdentifier("hauntedmc");
    }

    @Test
    void exposesRegistryFailuresAsIoFailures() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        when(directory.snapshotActiveIdentities()).thenReturn(Map.of());
        when(directory.findByIdentifier("HauntedMC"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));

        DataRegistryPlayerIdentityLookup lookup = new DataRegistryPlayerIdentityLookup(directory);

        IOException failure = assertThrows(IOException.class, () -> lookup.find("HauntedMC"));

        assertEquals("offline", failure.getCause().getMessage());
    }

    private static PlayerIdentity identity(String username) {
        return new PlayerIdentity(42L, UUID.randomUUID(), username);
    }
}
