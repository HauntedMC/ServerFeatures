package nl.hauntedmc.serverfeatures.framework.persistence;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import org.hibernate.Session;
import org.hibernate.query.Query;
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
        verify(session, never()).createQuery(
                "SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid",
                PlayerEntity.class
        );
    }

    @Test
    void resolveManagedFallsBackToPersistedPlayerWhenActiveCacheIsNotReady() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        Session session = mock(Session.class);
        UUID uuid = UUID.randomUUID();
        PlayerEntity persisted = new PlayerEntity();
        Query<PlayerEntity> query = mock(Query.class);

        when(directory.findActiveIdentityCached(uuid)).thenReturn(Optional.empty());
        when(session.createQuery("SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class))
                .thenReturn(query);
        when(query.setParameter("uuid", uuid.toString())).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.uniqueResultOptional()).thenReturn(Optional.of(persisted));

        PlayerEntity result = new PlayerEntityResolver(directory).resolveManaged(session, uuid);

        assertSame(persisted, result);
        verify(directory, never()).findByUuid(uuid);
    }

    @Test
    void resolveManagedReturnsNullWhenDataRegistryDoesNotKnowPersistedPlayer() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        Session session = mock(Session.class);
        UUID uuid = UUID.randomUUID();
        Query<PlayerEntity> query = mock(Query.class);

        when(directory.findActiveIdentityCached(uuid)).thenReturn(Optional.empty());
        when(session.createQuery("SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class))
                .thenReturn(query);
        when(query.setParameter("uuid", uuid.toString())).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.uniqueResultOptional()).thenReturn(Optional.empty());

        PlayerEntity result = new PlayerEntityResolver(directory).resolveManaged(session, uuid);

        assertNull(result);
        verify(session, never()).getReference(PlayerEntity.class, 0L);
    }
}
