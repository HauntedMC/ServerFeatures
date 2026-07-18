package nl.hauntedmc.serverfeatures.features.commandlogger.service;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.serverfeatures.features.commandlogger.entity.CommandExecutionEntity;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandLogServiceTest {

    @Test
    void logServerCommandSkipsPlayerEntityCreationWhenPlayerRowIsMissing() {
        CommandLogService service = new CommandLogService(null, missingPlayerRepository("11111111-1111-1111-1111-111111111111"));
        Player player = InterfaceProxy.of(Player.class, Map.of(
                "getUniqueId", args -> UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "getName", args -> "Remy"
        ));

        List<Object> persisted = new ArrayList<>();
        List<Object> merged = new ArrayList<>();
        Query<PlayerEntity> playerQuery = queryReturning(null);
        Session session = session(playerQuery, persisted, merged);

        service.logServerCommand(session, "survival", 123L, player, "spawn");

        assertTrue(persisted.stream().noneMatch(PlayerEntity.class::isInstance));
        assertTrue(merged.isEmpty());
        assertEquals(1, persisted.size());

        CommandExecutionEntity entry = assertInstanceOf(CommandExecutionEntity.class, persisted.getFirst());
        assertNull(entry.getPlayer());
        assertEquals("survival", entry.getServer());
        assertEquals("spawn", entry.getCommand());
        assertEquals(123L, entry.getTimestamp());
    }

    private static PlayerRepository missingPlayerRepository(String uuid) {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        when(playerRepository.getActiveIdentity(uuid)).thenReturn(Optional.empty());
        when(playerRepository.findIdentityByUUID(uuid)).thenReturn(Optional.empty());
        return playerRepository;
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
