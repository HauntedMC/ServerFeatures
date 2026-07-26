package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.DataProviderScope;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseProvider;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDatabaseProvider;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.framework.config.MainConfigHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureDataManagerTest {

    private ServerFeatures plugin;
    private MainConfigHandler config;

    @BeforeEach
    void setUp() {
        plugin = mock(ServerFeatures.class);
        config = mock(MainConfigHandler.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("FeatureDataManagerTest"));
        when(plugin.getConfigHandler()).thenReturn(config);
        when(config.getGlobalSetting(anyString(), eq(String.class), anyString())).thenReturn("validate");
    }

    @Test
    void strictRegistrationTracksConnectedProviderAndCleansItUp() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataProviderScope scope = scope(api);
        DatabaseProvider provider = mock(DatabaseProvider.class);
        when(provider.isConnected()).thenReturn(true);
        when(scope.registerDatabaseOrThrow(DatabaseType.MYSQL, "default")).thenReturn(provider);
        FeatureDataManager manager = new FeatureDataManager(plugin, api);

        manager.initDataProvider("Queue");

        assertSame(provider, manager.registerConnection("main", DatabaseType.MYSQL, "default").orElseThrow());
        assertEquals(1, manager.getActiveConnCount());
        manager.closeAllConnections();

        verify(scope).registerDatabaseOrThrow(DatabaseType.MYSQL, "default");
        verify(scope).close();
        assertEquals(0, manager.getActiveConnCount());
    }

    @Test
    void registrationFailureAndDisconnectedProviderAreRejected() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataProviderScope scope = scope(api);
        DatabaseProvider disconnected = mock(DatabaseProvider.class);
        when(disconnected.isConnected()).thenReturn(false);
        when(scope.registerDatabaseOrThrow(DatabaseType.MYSQL, "default"))
                .thenThrow(new IllegalStateException("missing configuration"))
                .thenReturn(disconnected);
        FeatureDataManager manager = new FeatureDataManager(plugin, api);
        manager.initDataProvider("Queue");

        assertTrue(manager.registerConnection("main", DatabaseType.MYSQL, "default").isEmpty());
        assertTrue(manager.registerConnection("main", DatabaseType.MYSQL, "default").isEmpty());
    }

    @Test
    void typedDataAccessUsesTheProviderHandle() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataProviderScope scope = scope(api);
        MessagingDatabaseProvider provider = mock(MessagingDatabaseProvider.class);
        MessagingDataAccess access = mock(MessagingDataAccess.class);
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataAccess()).thenReturn(access);
        when(scope.registerDatabaseOrThrow(DatabaseType.REDIS_MESSAGING, "hauntedmc")).thenReturn(provider);
        FeatureDataManager manager = new FeatureDataManager(plugin, api);
        manager.initDataProvider("Queue");

        Optional<MessagingDataAccess> result = manager.registerRedisMessagingDataAccess("redis", "hauntedmc");

        assertSame(access, result.orElseThrow());
        verify(scope).registerDatabaseOrThrow(DatabaseType.REDIS_MESSAGING, "hauntedmc");
    }

    @Test
    void ormContextsUseTheBoundApiAndRelationalDataSource() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataProviderScope scope = scope(api);
        RelationalDatabaseProvider provider = mock(RelationalDatabaseProvider.class);
        DataSource dataSource = mock(DataSource.class);
        ORMContext ormContext = mock(ORMContext.class);
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSource()).thenReturn(dataSource);
        when(scope.registerDatabaseOrThrow(DatabaseType.MYSQL, "default")).thenReturn(provider);
        when(api.createOrmContext(same(dataSource), any(), eq("validate"), eq(String.class))).thenReturn(ormContext);
        FeatureDataManager manager = new FeatureDataManager(plugin, api);
        manager.initDataProvider("Queue");

        assertTrue(manager.registerConnection("main", DatabaseType.MYSQL, "default").isPresent());
        assertSame(ormContext, manager.createORMContext("main", String.class).orElseThrow());
        manager.closeAllConnections();

        verify(api).createOrmContext(same(dataSource), any(), eq("validate"), eq(String.class));
        verify(ormContext).shutdown();
    }

    @Test
    void nonRelationalDataSourcesDoNotCreateOrmContexts() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataProviderScope scope = scope(api);
        DatabaseProvider provider = mock(DatabaseProvider.class);
        when(provider.isConnected()).thenReturn(true);
        when(scope.registerDatabaseOrThrow(DatabaseType.REDIS, "default")).thenReturn(provider);
        FeatureDataManager manager = new FeatureDataManager(plugin, api);
        manager.initDataProvider("Queue");

        assertTrue(manager.registerConnection("cache", DatabaseType.REDIS, "default").isPresent());
        assertTrue(manager.createORMContext("cache", String.class).isEmpty());
    }

    @Test
    void cleanupContinuesWhenDataProviderRejectsUnregistration() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataProviderScope scope = scope(api);
        DatabaseProvider provider = mock(DatabaseProvider.class);
        when(provider.isConnected()).thenReturn(true);
        when(scope.registerDatabaseOrThrow(DatabaseType.MYSQL, "default")).thenReturn(provider);
        doThrow(new IllegalStateException("closed")).when(scope).close();
        FeatureDataManager manager = new FeatureDataManager(plugin, api);
        manager.initDataProvider("Queue");

        assertTrue(manager.registerConnection("main", DatabaseType.MYSQL, "default").isPresent());
        manager.closeAllConnections();

        assertEquals(0, manager.getActiveConnCount());
        verify(scope).close();
    }

    private static DataProviderScope scope(DataProviderAPI api) {
        DataProviderScope scope = mock(DataProviderScope.class);
        when(api.scope(anyString())).thenReturn(scope);
        return scope;
    }
}
