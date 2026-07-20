package nl.hauntedmc.serverfeatures.framework.persistence;

import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerIdentityResolverTest {

    @Test
    void returnsTheCachedPublicIdentity() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        UUID uuid = UUID.randomUUID();
        PlayerIdentity identity = new PlayerIdentity(12L, uuid, "Alice");
        when(directory.findActiveIdentityCached(uuid)).thenReturn(Optional.of(identity));

        assertEquals(identity, new PlayerIdentityResolver(directory).findActiveByUuid(uuid).orElseThrow());
    }

    @Test
    void returnsEmptyWhenNoCachedIdentityExists() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        when(directory.findActiveIdentityCached("not-a-uuid")).thenReturn(Optional.empty());

        assertTrue(new PlayerIdentityResolver(directory).findActiveByUuid("not-a-uuid").isEmpty());
    }

    @Test
    void findsActiveIdentitiesByUsernameIgnoringCase() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        PlayerIdentity identity = new PlayerIdentity(43L, UUID.randomUUID(), "Alice");
        when(directory.snapshotActiveIdentities()).thenReturn(Map.of("alice", identity));

        assertEquals(identity, new PlayerIdentityResolver(directory).findActiveByUsername("aLiCe").orElseThrow());
    }
}
