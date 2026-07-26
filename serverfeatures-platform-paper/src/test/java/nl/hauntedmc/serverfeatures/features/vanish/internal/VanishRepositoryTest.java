package nl.hauntedmc.serverfeatures.features.vanish.internal;

import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VanishRepositoryTest {

    @Test
    void findExistingPlayerIdReturnsNullForAnUnknownIdentity() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        String uuid = "66666666-6666-6666-6666-666666666666";
        when(directory.findActiveIdentityCached(uuid)).thenReturn(Optional.empty());

        assertNull(new VanishRepository(null, directory).findExistingPlayerId(uuid));
    }

    @Test
    void findExistingPlayerIdUsesTheDataRegistryIdentityId() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        UUID uuid = UUID.fromString("88888888-8888-8888-8888-888888888888");
        when(directory.findActiveIdentityCached(uuid.toString()))
                .thenReturn(Optional.of(new PlayerIdentity(88L, uuid, "OldName")));

        assertEquals(88L, new VanishRepository(null, directory).findExistingPlayerId(uuid.toString()));
    }
}
