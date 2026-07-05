package nl.hauntedmc.serverfeatures.features.vanish.internal;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanishRepositoryTest {

    @Test
    void findExistingPlayerEntityDoesNotCreateMissingPlayerRows() {
        VanishRepository repository = new VanishRepository(null);
        List<Object> persisted = new ArrayList<>();
        List<Object> merged = new ArrayList<>();
        Session session = session(queryReturning(null), persisted, merged);

        PlayerEntity playerEntity = repository.findExistingPlayerEntity(
                session,
                "66666666-6666-6666-6666-666666666666",
                "Remy"
        );

        assertNull(playerEntity);
        assertTrue(persisted.isEmpty());
        assertTrue(merged.isEmpty());
    }

    @Test
    void upsertVanishSkipsPersistenceWhenPlayerRowIsMissing() {
        VanishRepository repository = new VanishRepository(null);
        List<Object> persisted = new ArrayList<>();
        List<Object> merged = new ArrayList<>();
        Session session = session(queryReturning(null), persisted, merged);

        repository.upsertVanish(session, "77777777-7777-7777-7777-777777777777", "Remy", true);

        assertTrue(persisted.isEmpty());
        assertTrue(merged.isEmpty());
    }

    @Test
    void findExistingPlayerEntityRefreshesUsernameWithoutCreatingRows() {
        VanishRepository repository = new VanishRepository(null);
        PlayerEntity playerEntity = new PlayerEntity();
        playerEntity.setUsername("OldName");

        List<Object> persisted = new ArrayList<>();
        List<Object> merged = new ArrayList<>();
        Session session = session(queryReturning(playerEntity), persisted, merged);

        PlayerEntity resolved = repository.findExistingPlayerEntity(
                session,
                "88888888-8888-8888-8888-888888888888",
                "NewName"
        );

        assertSame(playerEntity, resolved);
        assertTrue(persisted.isEmpty());
        assertEquals(List.of(playerEntity), merged);
        assertEquals("NewName", playerEntity.getUsername());
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
