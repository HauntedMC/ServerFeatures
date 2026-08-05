package nl.hauntedmc.serverfeatures.features.liquidtank.internal.packet;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketHandlerTest {

    @Test
    void entityIdentityIsStableAndUniquePerVisualLayer() {
        PacketHandler first = new PacketHandler(new Location(null, 1, 2, 3));
        PacketHandler second = new PacketHandler(new Location(null, 1, 2, 3));
        int identity = first.entityId();

        assertEquals(identity, first.entityId());
        assertNotEquals(identity, second.entityId());
        assertTrue(identity < 0);
    }
}
