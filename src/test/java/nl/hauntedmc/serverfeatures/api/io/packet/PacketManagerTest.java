package nl.hauntedmc.serverfeatures.api.io.packet;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacketManagerTest {

    @Test
    void sendUnicastSendsAllPacketsToTargetPlayer() {
        AtomicInteger calls = new AtomicInteger();
        Packet p1 = player -> calls.incrementAndGet();
        Packet p2 = player -> calls.incrementAndGet();

        PacketManager.sendUnicast((Player) null, p1, p2);

        assertEquals(2, calls.get());
    }

    @Test
    void sendMulticastSendsAllPacketsToEveryTargetPlayer() {
        AtomicInteger calls = new AtomicInteger();
        Packet packet = player -> calls.incrementAndGet();

        List<Player> players = new ArrayList<>();
        players.add(null);
        players.add(null);
        players.add(null);

        PacketManager.sendMulticast(players, packet, packet);

        assertEquals(6, calls.get());
    }
}
