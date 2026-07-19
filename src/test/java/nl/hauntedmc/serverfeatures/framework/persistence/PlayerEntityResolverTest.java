package nl.hauntedmc.serverfeatures.framework.persistence;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerEntityResolverTest {

    @Test
    void resolveManagedUsesActiveIdentityWithoutCreatingOrUpdatingPlayer() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        Session session = mock(Session.class);
        PlayerEntity managed = new PlayerEntity();
        UUID uuid = UUID.randomUUID();
        PlayerIdentity identity = new PlayerIdentity(12L, uuid, "Alice");

        when(directory.findActiveIdentityCached(uuid)).thenReturn(Optional.of(identity));
        when(session.getReference(PlayerEntity.class, 12L)).thenReturn(managed);

        PlayerEntity result = new PlayerEntityResolver(directory).resolveManaged(session, uuid);

        assertSame(managed, result);
        verify(directory, never()).findByUuid(uuid);
    }

    @Test
    void resolveManagedDoesNotQueryPersistenceInsideFeatureTransaction() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        Session session = mock(Session.class);
        UUID uuid = UUID.randomUUID();

        when(directory.findActiveIdentityCached(uuid)).thenReturn(Optional.empty());

        PlayerEntity result = new PlayerEntityResolver(directory).resolveManaged(session, uuid);

        assertNull(result);
        verify(directory, never()).findByUuid(uuid);
    }

    @Test
    void resolveManagedReturnsNullWhenDataRegistryDoesNotKnowPlayer() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        Session session = mock(Session.class);
        UUID uuid = UUID.randomUUID();

        when(directory.findActiveIdentityCached(uuid)).thenReturn(Optional.empty());

        PlayerEntity result = new PlayerEntityResolver(directory).resolveManaged(session, uuid);

        assertNull(result);
        verify(session, never()).getReference(PlayerEntity.class, 0L);
    }
}
