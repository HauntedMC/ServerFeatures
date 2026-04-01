package nl.hauntedmc.serverfeatures.features.vanish.internal.messaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanishStateMessageTest {

    @Test
    void constructorStoresPayloadAndType() {
        VanishStateMessage message = new VanishStateMessage(
                "vanish_update",
                "uuid-1",
                "Remy",
                true,
                "hub"
        );

        assertEquals("vanish_update", message.getType());
        assertEquals("uuid-1", message.getPlayerUuid());
        assertEquals("Remy", message.getPlayerName());
        assertTrue(message.isVanished());
        assertEquals("hub", message.getServer());
    }
}

