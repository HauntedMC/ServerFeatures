package nl.hauntedmc.serverfeatures.features.chatlog.internal.services;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.serverfeatures.features.chatlog.entities.ChatMessageEntity;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatLogServiceTest {

    @Test
    void addMessageSkipsPersistenceWhenPlayerRowIsMissing() {
        ChatLogService service = new ChatLogService(null);
        Player player = player("22222222-2222-2222-2222-222222222222", "Remy");
        List<Object> persisted = new ArrayList<>();
        List<Object> merged = new ArrayList<>();
        Session session = session(queryReturning(null), persisted, merged);

        boolean stored = service.addMessage(session, "survival", 456L, player, "hello");

        assertFalse(stored);
        assertTrue(persisted.isEmpty());
        assertTrue(merged.isEmpty());
    }

    @Test
    void addMessageUsesExistingPlayerRowAndRefreshesUsername() {
        ChatLogService service = new ChatLogService(null);
        Player player = player("33333333-3333-3333-3333-333333333333", "NewName");
        PlayerEntity playerEntity = new PlayerEntity();
        playerEntity.setUsername("OldName");

        List<Object> persisted = new ArrayList<>();
        List<Object> merged = new ArrayList<>();
        Session session = session(queryReturning(playerEntity), persisted, merged);

        boolean stored = service.addMessage(session, "survival", 789L, player, "message");

        assertTrue(stored);
        assertEquals(List.of(playerEntity), merged);
        assertEquals(1, persisted.size());

        ChatMessageEntity message = assertInstanceOf(ChatMessageEntity.class, persisted.getFirst());
        assertSame(playerEntity, message.getPlayer());
        assertEquals("message", message.getMessage());
        assertEquals("NewName", playerEntity.getUsername());
    }

    private static Player player(String uuid, String name) {
        return InterfaceProxy.of(Player.class, Map.of(
                "getUniqueId", args -> UUID.fromString(uuid),
                "getName", args -> name
        ));
    }

    private static Query<PlayerEntity> queryReturning(PlayerEntity playerEntity) {
        return (Query<PlayerEntity>) Proxy.newProxyInstance(
                Query.class.getClassLoader(),
                new Class<?>[]{Query.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setParameter" -> proxy;
                    case "uniqueResult" -> playerEntity;
                    default -> null;
                }
        );
    }

    private static Session session(Query<PlayerEntity> playerQuery, List<Object> persisted, List<Object> merged) {
        return InterfaceProxy.of(Session.class, Map.of(
                "createQuery", args -> playerQuery,
                "persist", args -> {
                    persisted.add(args[0]);
                    return null;
                },
                "merge", args -> {
                    merged.add(args[0]);
                    return args[0];
                }
        ));
    }
}
