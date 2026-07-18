package nl.hauntedmc.serverfeatures.features.vanish.internal;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VanishRepositoryTest {

    @Test
    void findExistingPlayerEntityDoesNotCreateMissingPlayerRows() {
        PlayerRepository playerRepository = mockPlayerRepositoryMissing("66666666-6666-6666-6666-666666666666");
        VanishRepository repository = new VanishRepository(null, playerRepository);
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
        PlayerRepository playerRepository = mockPlayerRepositoryMissing("77777777-7777-7777-7777-777777777777");
        VanishRepository repository = new VanishRepository(null, playerRepository);
        List<Object> persisted = new ArrayList<>();
        List<Object> merged = new ArrayList<>();
        Session session = session(queryReturning(null), persisted, merged);

        repository.upsertVanish(session, "77777777-7777-7777-7777-777777777777", "Remy", true);

        assertTrue(persisted.isEmpty());
        assertTrue(merged.isEmpty());
    }

    @Test
    void findExistingPlayerEntityUsesDataRegistryIdentityWithoutUpdatingUsername() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        when(playerRepository.getActiveIdentity("88888888-8888-8888-8888-888888888888"))
                .thenReturn(Optional.of(new PlayerRepository.PlayerIdentity(
                        88L,
                        "88888888-8888-8888-8888-888888888888",
                        "OldName"
                )));
        VanishRepository repository = new VanishRepository(null, playerRepository);
        PlayerEntity playerEntity = new PlayerEntity();
        playerEntity.setId(88L);
        playerEntity.setUsername("OldName");

        List<Object> persisted = new ArrayList<>();
        List<Object> merged = new ArrayList<>();
        Session session = session(queryReturning(null), persisted, merged, playerEntity);

        PlayerEntity resolved = repository.findExistingPlayerEntity(
                session,
                "88888888-8888-8888-8888-888888888888",
                "NewName"
        );

        assertSame(playerEntity, resolved);
        assertTrue(persisted.isEmpty());
        assertTrue(merged.isEmpty());
        assertEquals("OldName", playerEntity.getUsername());
    }

    private static PlayerRepository mockPlayerRepositoryMissing(String uuid) {
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
        return session(playerQuery, persisted, merged, null);
    }

    private static Session session(Query<PlayerEntity> playerQuery, List<Object> persisted, List<Object> merged, PlayerEntity reference) {
        return InterfaceProxy.of(Session.class, Map.of(
                "createQuery", args -> playerQuery,
                "getReference", args -> reference,
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
