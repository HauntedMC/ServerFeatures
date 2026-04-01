package nl.hauntedmc.serverfeatures.features.afk.internal.engine.event;

import nl.hauntedmc.serverfeatures.features.afk.internal.engine.util.Movement;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AfkEventTest {

    private static Player player() {
        return InterfaceProxy.of(Player.class, Map.of());
    }

    @Test
    void ofFactoryRetainsGivenValues() {
        Player p = player();
        Movement movement = new Movement(1, 2, 3, 4f, 5f, 6, 7, 8, 9f, 10f);

        AfkEvent event = AfkEvent.of(p, AfkEventType.MOVE, 123L, "payload", movement);

        assertSame(p, event.player());
        assertEquals(AfkEventType.MOVE, event.type());
        assertEquals(123L, event.timestamp());
        assertEquals("payload", event.payload());
        assertSame(movement, event.movement());
    }

    @Test
    void commandFactorySetsCommandPayload() {
        Player p = player();

        AfkEvent event = AfkEvent.command(p, "/spawn");

        assertSame(p, event.player());
        assertEquals(AfkEventType.COMMAND, event.type());
        assertEquals("/spawn", event.payload());
        assertNull(event.movement());
    }

    @Test
    void moveAndTeleportFactoriesSetMovement() {
        Player p = player();
        Movement movement = new Movement(0, 0, 0, 0f, 0f, 1, 0, 0, 0f, 0f);

        AfkEvent move = AfkEvent.move(p, movement);
        AfkEvent teleport = AfkEvent.teleport(p, movement);

        assertEquals(AfkEventType.MOVE, move.type());
        assertSame(movement, move.movement());
        assertEquals(AfkEventType.TELEPORT, teleport.type());
        assertSame(movement, teleport.movement());
    }

    @Test
    void simpleFactoryUsesProvidedType() {
        Player p = player();

        AfkEvent event = AfkEvent.simple(p, AfkEventType.CHAT);

        assertSame(p, event.player());
        assertEquals(AfkEventType.CHAT, event.type());
        assertNull(event.payload());
        assertNull(event.movement());
    }
}

