package nl.hauntedmc.serverfeatures.features.commandrelay.audit;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.serverfeatures.features.commandrelay.CommandRelay;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureTaskManager;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommandRelayAuditLogServiceTest {

    @Test
    void normalizeTrimsDropsBlankValuesAndBoundsLength() {
        assertNull(CommandRelayAuditLogService.normalize(null, 10));
        assertNull(CommandRelayAuditLogService.normalize("   ", 10));
        assertEquals("value", CommandRelayAuditLogService.normalize("  value  ", 10));
        assertEquals("12345", CommandRelayAuditLogService.normalize("123456789", 5));
    }

    @Test
    void persistMapsAllAuditFields() {
        CommandRelay feature = mock(CommandRelay.class);
        CommandRelayAuditLogService service = new CommandRelayAuditLogService(feature, null);
        List<Object> persisted = new ArrayList<>();

        service.persist(
                session(persisted),
                "executed",
                "survival.commandrelay.command",
                "proxy",
                "say",
                "say hello",
                null,
                123L
        );

        CommandRelayAuditLogEntity entry = assertInstanceOf(
                CommandRelayAuditLogEntity.class,
                persisted.getFirst()
        );
        assertEquals("executed", entry.getEventType());
        assertEquals("survival.commandrelay.command", entry.getRelayChannel());
        assertEquals("proxy", entry.getOriginServer());
        assertEquals("say", entry.getCommandAlias());
        assertEquals("say hello", entry.getCommandText());
        assertNull(entry.getDetails());
        assertEquals(123L, entry.getCreatedAt());
    }

    @Test
    void logEventSchedulesNormalizedPersistenceThroughFeatureTaskManager() {
        CommandRelay feature = featureWithTasks();
        FeatureTaskManager tasks = feature.getLifecycleManager().getTaskManager();
        when(tasks.runAsync(any(Runnable.class))).thenAnswer(invocation -> {
            try {
                invocation.getArgument(0, Runnable.class).run();
                return CompletableFuture.completedFuture(null);
            } catch (Throwable throwable) {
                return CompletableFuture.failedFuture(throwable);
            }
        });

        ORMContext orm = mock(ORMContext.class);
        List<Object> persisted = new ArrayList<>();
        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session(persisted));
        });

        CommandRelayAuditLogService service = new CommandRelayAuditLogService(feature, orm);
        service.logEvent(
                "  executed  ",
                "  survival.commandrelay.command  ",
                "  proxy  ",
                "  say  ",
                "  say hello  ",
                "   "
        );

        CommandRelayAuditLogEntity entry = assertInstanceOf(
                CommandRelayAuditLogEntity.class,
                persisted.getFirst()
        );
        assertEquals("executed", entry.getEventType());
        assertEquals("survival.commandrelay.command", entry.getRelayChannel());
        assertEquals("proxy", entry.getOriginServer());
        assertEquals("say", entry.getCommandAlias());
        assertEquals("say hello", entry.getCommandText());
        assertNull(entry.getDetails());
    }

    @Test
    void asynchronousPersistenceFailureIsContainedAndLogged() {
        CommandRelay feature = featureWithTasks();
        when(feature.getLifecycleManager().getTaskManager().runAsync(any(Runnable.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database down")));
        CommandRelayAuditLogService service = new CommandRelayAuditLogService(feature, mock(ORMContext.class));

        service.logEvent("executed", "stream", "proxy", "say", "say hello", null);

        verify(feature.getLogger()).warning(contains("database down"));
    }

    @Test
    void unavailableOrmDisablesAuditWithoutTouchingFeatureLifecycle() {
        CommandRelay feature = mock(CommandRelay.class);
        CommandRelayAuditLogService service = new CommandRelayAuditLogService(feature, null);

        service.logEvent("executed", "stream", "proxy", "say", "say hello", null);

        verifyNoInteractions(feature);
    }

    private static CommandRelay featureWithTasks() {
        CommandRelay feature = mock(CommandRelay.class);
        FeatureLifecycleManager lifecycle = mock(FeatureLifecycleManager.class);
        FeatureTaskManager tasks = mock(FeatureTaskManager.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        when(feature.getLifecycleManager()).thenReturn(lifecycle);
        when(feature.getLogger()).thenReturn(logger);
        when(lifecycle.getTaskManager()).thenReturn(tasks);
        return feature;
    }

    private static Session session(List<Object> persisted) {
        return InterfaceProxy.of(Session.class, Map.of("persist", arguments -> {
            persisted.add(arguments[0]);
            return null;
        }));
    }
}
