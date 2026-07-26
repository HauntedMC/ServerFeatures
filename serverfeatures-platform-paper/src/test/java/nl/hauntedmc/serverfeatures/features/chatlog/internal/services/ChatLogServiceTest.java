package nl.hauntedmc.serverfeatures.features.chatlog.internal.services;

import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.serverfeatures.features.chatlog.entities.ChatMessageEntity;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatLogServiceTest {

    @Test
    void addMessageSkipsUnknownActiveIdentitiesInCompatibilityPath() {
        UUID uuid = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PlayerDirectory directory = mock(PlayerDirectory.class);
        when(directory.findActiveIdentityCached(uuid)).thenReturn(Optional.empty());

        assertFalse(new ChatLogService(null, directory).addMessage(
                session(new ArrayList<>()),
                "survival",
                456L,
                player(uuid),
                "hello"
        ));
    }

    @Test
    void addMessagePersistsTheDataRegistryPlayerId() {
        UUID uuid = UUID.fromString("33333333-3333-3333-3333-333333333333");
        PlayerDirectory directory = mock(PlayerDirectory.class);
        when(directory.findActiveIdentityCached(uuid))
                .thenReturn(Optional.of(new PlayerIdentity(33L, uuid, "OldName")));
        List<Object> persisted = new ArrayList<>();

        assertTrue(new ChatLogService(null, directory).addMessage(
                session(persisted),
                "survival",
                789L,
                player(uuid),
                "message"
        ));
        ChatMessageEntity message = assertInstanceOf(ChatMessageEntity.class, persisted.getFirst());
        assertEquals(33L, message.getPlayerId());
        assertEquals("message", message.getMessage());
    }

    @Test
    void resolvedPlayerIdRemainsUsableAfterActiveIdentityDisappears() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        List<Object> persisted = new ArrayList<>();
        ChatLogService service = new ChatLogService(null, directory);

        assertTrue(service.addMessage(session(persisted), "survival", 999L, 44L, "disconnect race"));

        ChatMessageEntity message = assertInstanceOf(ChatMessageEntity.class, persisted.getFirst());
        assertEquals(44L, message.getPlayerId());
        assertEquals("disconnect race", message.getMessage());
    }

    private static Player player(UUID uuid) {
        return InterfaceProxy.of(Player.class, Map.of("getUniqueId", args -> uuid));
    }

    private static Session session(List<Object> persisted) {
        return InterfaceProxy.of(Session.class, Map.of("persist", args -> {
            persisted.add(args[0]);
            return null;
        }));
    }
}
