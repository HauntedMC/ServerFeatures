package nl.hauntedmc.serverfeatures.features.vanish.internal;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VanishRepositoryTest {

    @Test
    void findExistingPlayerEntityDoesNotCreateMissingPlayerRows() {
        PlayerDirectory playerDirectory = mockPlayerDirectoryMissing("66666666-6666-6666-6666-666666666666");
        VanishRepository repository = new VanishRepository(null, playerDirectory);
        List<Object> persisted = new ArrayList<>();
        List<Object> merged = new ArrayList<>();
        Session session = session(queryReturning(null), persisted, merged);

        PlayerEntity playerEntity = repository.findExistingPlayerEntity(
                session,
                "66666666-6666-6666-6666-666666666666"
        );

        assertNull(playerEntity);
        assertTrue(persisted.isEmpty());
        assertTrue(merged.isEmpty());
    }

    @Test
    void upsertVanishSkipsPersistenceWhenPlayerRowIsMissing() {
        PlayerDirectory playerDirectory = mockPlayerDirectoryMissing("77777777-7777-7777-7777-777777777777");
        VanishRepository repository = new VanishRepository(null, playerDirectory);
        List<Object> persisted = new ArrayList<>();
        List<Object> merged = new ArrayList<>();
        Session session = session(queryReturning(null), persisted, merged);

        repository.upsertVanish(session, "77777777-7777-7777-7777-777777777777", "Remy", true);

        assertTrue(persisted.isEmpty());
        assertTrue(merged.isEmpty());
    }

    @Test
    void findExistingPlayerEntityUsesDataRegistryIdentityWithoutUpdatingUsername() {
        PlayerDirectory playerDirectory = mock(PlayerDirectory.class);
        UUID uuid = UUID.fromString("88888888-8888-8888-8888-888888888888");
        when(playerDirectory.getActiveIdentity(uuid.toString()))
                .thenReturn(Optional.of(new PlayerIdentity(
                        88L,
                        uuid,
                        "OldName"
                )));
        VanishRepository repository = new VanishRepository(null, playerDirectory);
        PlayerEntity playerEntity = new PlayerEntity();
        playerEntity.setId(88L);
        playerEntity.setUsername("OldName");

        List<Object> persisted = new ArrayList<>();
        List<Object> merged = new ArrayList<>();
        Session session = session(queryReturning(null), persisted, merged, playerEntity);

        PlayerEntity resolved = repository.findExistingPlayerEntity(
                session,
                "88888888-8888-8888-8888-888888888888"
        );

        assertSame(playerEntity, resolved);
        assertTrue(persisted.isEmpty());
        assertTrue(merged.isEmpty());
        assertEquals("OldName", playerEntity.getUsername());
    }

    private static PlayerDirectory mockPlayerDirectoryMissing(String uuid) {
        PlayerDirectory playerDirectory = mock(PlayerDirectory.class);
        when(playerDirectory.getActiveIdentity(uuid)).thenReturn(Optional.empty());
        when(playerDirectory.findByUuid(uuid)).thenReturn(Optional.empty());
        return playerDirectory;
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
