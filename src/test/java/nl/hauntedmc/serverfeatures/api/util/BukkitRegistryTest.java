package nl.hauntedmc.serverfeatures.api.util;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BukkitRegistryTest {

    @Test
    void deserializeNamespacedKeySupportsNamespacedAndLegacyFormats() {
        NamespacedKey direct = BukkitRegistry.deserializeNamespacedKey("minecraft:entity.player.levelup");
        NamespacedKey dot = BukkitRegistry.deserializeNamespacedKey("entity.player.levelup");
        NamespacedKey enumStyle = BukkitRegistry.deserializeNamespacedKey("ENTITY_PLAYER_LEVELUP");

        assertEquals("minecraft", direct.getNamespace());
        assertEquals("entity.player.levelup", direct.getKey());
        assertEquals(direct, dot);
        assertEquals(direct, enumStyle);
    }

    @Test
    void deserializeNamespacedKeyTrimsInputAndReturnsNullForInvalidKey() {
        NamespacedKey trimmed = BukkitRegistry.deserializeNamespacedKey("  minecraft:stone ");
        NamespacedKey invalid = BukkitRegistry.deserializeNamespacedKey("::");

        assertEquals("minecraft", trimmed.getNamespace());
        assertEquals("stone", trimmed.getKey());
        assertNull(invalid);
    }
}
