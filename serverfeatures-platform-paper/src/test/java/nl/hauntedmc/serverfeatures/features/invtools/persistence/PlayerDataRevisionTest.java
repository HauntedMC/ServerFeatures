package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerDataRevisionTest {

    @Test
    void acceptsOnlyCanonicalLowercaseSha256Digests() {
        assertDoesNotThrow(() -> new PlayerDataRevision("0123456789abcdef".repeat(4)));
        assertThrows(IllegalArgumentException.class, () -> new PlayerDataRevision(""));
        assertThrows(IllegalArgumentException.class, () -> new PlayerDataRevision("0".repeat(63)));
        assertThrows(IllegalArgumentException.class, () -> new PlayerDataRevision("0".repeat(65)));
        assertThrows(IllegalArgumentException.class, () ->
                new PlayerDataRevision("0123456789ABCDEF".repeat(4)));
        assertThrows(IllegalArgumentException.class, () -> new PlayerDataRevision("g".repeat(64)));
    }
}
