package nl.hauntedmc.serverfeatures.api.hook;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViaVersionHookTest {

    @Test
    void unavailableLookupReportsFalseAndThrowsOnProtocolQueries() {
        ViaVersionHook hook = new ViaVersionHook(new ViaVersionHook.ProtocolLookup() {
            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public int serverProtocolId() {
                return 0;
            }

            @Override
            public int clientProtocolId(UUID playerId) {
                return 0;
            }
        });

        assertFalse(hook.isAvailable());
        assertThrows(IllegalStateException.class, hook::getServerNativeProtocolId);
        assertThrows(IllegalStateException.class, () -> hook.getClientProtocolId(player(UUID.randomUUID())));
    }

    @Test
    void resolvesServerAndClientProtocolNamesFromIds() {
        int serverProtocol = 47;
        int clientProtocol = 763;
        AtomicReference<UUID> seenPlayerId = new AtomicReference<>();

        ViaVersionHook hook = new ViaVersionHook(new ViaVersionHook.ProtocolLookup() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public int serverProtocolId() {
                return serverProtocol;
            }

            @Override
            public int clientProtocolId(UUID playerId) {
                seenPlayerId.set(playerId);
                return clientProtocol;
            }
        });

        UUID id = UUID.randomUUID();
        Player player = player(id);

        assertTrue(hook.isAvailable());
        assertEquals(serverProtocol, hook.getServerNativeProtocolId());
        assertEquals(ProtocolVersion.getProtocol(serverProtocol).getName(), hook.getServerNativeProtocolName());
        assertEquals(clientProtocol, hook.getClientProtocolId(player));
        assertEquals(id, seenPlayerId.get());
        assertEquals(ProtocolVersion.getProtocol(clientProtocol).getName(), hook.getClientProtocolName(player));
    }

    @Test
    void getClientProtocolIdRequiresPlayer() {
        ViaVersionHook hook = new ViaVersionHook(new ViaVersionHook.ProtocolLookup() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public int serverProtocolId() {
                return 47;
            }

            @Override
            public int clientProtocolId(UUID playerId) {
                return 47;
            }
        });

        assertThrows(NullPointerException.class, () -> hook.getClientProtocolId(null));
    }

    private static Player player(UUID id) {
        return InterfaceProxy.of(Player.class, Map.of("getUniqueId", args -> id));
    }
}
