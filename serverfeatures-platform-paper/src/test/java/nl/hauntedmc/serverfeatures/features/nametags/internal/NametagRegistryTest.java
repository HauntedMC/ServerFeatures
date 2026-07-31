package nl.hauntedmc.serverfeatures.features.nametags.internal;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NametagRegistryTest {

    @Test
    void staleUnregisterCannotRemoveReplacementNametag() {
        NametagRegistry registry = new NametagRegistry();
        UUID ownerId = UUID.randomUUID();
        Nametag original = nametag(ownerId, 100);
        Nametag replacement = nametag(ownerId, 101);

        registry.register(original);
        registry.register(replacement);

        assertFalse(registry.unregister(original));
        assertSame(replacement, registry.getNametag(ownerId).orElseThrow());
        assertTrue(registry.unregister(replacement));
        assertTrue(registry.getNametag(ownerId).isEmpty());
    }

    private static Nametag nametag(UUID ownerId, int entityId) {
        Nametag nametag = mock(Nametag.class);
        when(nametag.getNametagOwnerId()).thenReturn(ownerId);
        when(nametag.getEntityId()).thenReturn(entityId);
        return nametag;
    }
}
