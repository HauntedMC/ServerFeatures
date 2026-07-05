package nl.hauntedmc.serverfeatures.features.playerlanguage.service;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.logging.LoggerAdapter;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.serverfeatures.api.io.localization.Language;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageServiceTest {

    @Test
    void setDoesNotCacheWhenPlayerRowIsMissing() throws Exception {
        List<Object> persisted = new ArrayList<>();
        List<Object> merged = new ArrayList<>();
        Session session = session(queryReturning(null), persisted, merged);
        FakeOrmContext orm = allocateFakeOrmContext();
        orm.session = session;

        UUID uuid = UUID.randomUUID();
        LanguageService service = new LanguageService(orm);
        service.set(uuid, Language.NL);

        assertTrue(cache(service).isEmpty());
        assertTrue(persisted.isEmpty());
        assertTrue(merged.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Language> cache(LanguageService service) throws Exception {
        Field field = LanguageService.class.getDeclaredField("langCache");
        field.setAccessible(true);
        return (Map<UUID, Language>) field.get(service);
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

    private static FakeOrmContext allocateFakeOrmContext() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);
        return (FakeOrmContext) unsafe.allocateInstance(FakeOrmContext.class);
    }

    static class FakeOrmContext extends ORMContext {
        private Session session;

        FakeOrmContext() {
            super("test", null, (LoggerAdapter) null, "none");
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T runInTransaction(TransactionCallback<T> callback) {
            return callback.execute(session);
        }
    }
}
