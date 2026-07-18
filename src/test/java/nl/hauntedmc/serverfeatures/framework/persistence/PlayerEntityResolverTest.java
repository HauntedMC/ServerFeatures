package nl.hauntedmc.serverfeatures.framework.persistence;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
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
        PlayerRepository repository = mock(PlayerRepository.class);
        Session session = mock(Session.class);
        PlayerEntity managed = new PlayerEntity();
        UUID uuid = UUID.randomUUID();
        PlayerRepository.PlayerIdentity identity = new PlayerRepository.PlayerIdentity(12L, uuid.toString(), "Alice");

        when(repository.getActiveIdentity(uuid.toString())).thenReturn(Optional.of(identity));
        when(session.getReference(PlayerEntity.class, 12L)).thenReturn(managed);

        PlayerEntity result = new PlayerEntityResolver(repository).resolveManaged(session, uuid, "ChangedName");

        assertSame(managed, result);
        verify(repository, never()).findIdentityByUUID(uuid.toString());
        verify(repository, never()).getOrCreateActiveIdentity(uuid.toString(), "ChangedName");
    }

    @Test
    void resolveManagedFallsBackToPersistentIdentityWithoutCreatingPlayer() {
        PlayerRepository repository = mock(PlayerRepository.class);
        Session session = mock(Session.class);
        PlayerEntity managed = new PlayerEntity();
        UUID uuid = UUID.randomUUID();
        PlayerRepository.PlayerIdentity identity = new PlayerRepository.PlayerIdentity(21L, uuid.toString(), "Alice");

        when(repository.getActiveIdentity(uuid.toString())).thenReturn(Optional.empty());
        when(repository.findIdentityByUUID(uuid.toString())).thenReturn(Optional.of(identity));
        when(session.getReference(PlayerEntity.class, 21L)).thenReturn(managed);

        PlayerEntity result = new PlayerEntityResolver(repository).resolveManaged(session, uuid, "ChangedName");

        assertSame(managed, result);
        verify(repository, never()).getOrCreateActiveIdentity(uuid.toString(), "ChangedName");
    }

    @Test
    void resolveManagedReturnsNullWhenDataRegistryDoesNotKnowPlayer() {
        PlayerRepository repository = mock(PlayerRepository.class);
        Session session = mock(Session.class);
        UUID uuid = UUID.randomUUID();

        when(repository.getActiveIdentity(uuid.toString())).thenReturn(Optional.empty());
        when(repository.findIdentityByUUID(uuid.toString())).thenReturn(Optional.empty());

        PlayerEntity result = new PlayerEntityResolver(repository).resolveManaged(session, uuid, "Alice");

        assertNull(result);
        verify(repository, never()).getOrCreateActiveIdentity(uuid.toString(), "Alice");
        verify(session, never()).getReference(PlayerEntity.class, 0L);
    }
}
