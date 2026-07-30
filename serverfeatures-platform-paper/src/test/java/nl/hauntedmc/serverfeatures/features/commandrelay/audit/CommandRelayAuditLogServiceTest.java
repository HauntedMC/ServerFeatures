package nl.hauntedmc.serverfeatures.features.commandrelay.audit;

import nl.hauntedmc.serverfeatures.features.commandrelay.CommandRelay;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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
    void unavailableOrmDisablesAuditWithoutTouchingFeatureLifecycle() {
        CommandRelay feature = mock(CommandRelay.class);
        CommandRelayAuditLogService service = new CommandRelayAuditLogService(feature, null);

        service.logEvent("executed", "stream", "proxy", "say", "say hello", null);

        verifyNoInteractions(feature);
    }

    private static Session session(List<Object> persisted) {
        return InterfaceProxy.of(Session.class, Map.of("persist", arguments -> {
            persisted.add(arguments[0]);
            return null;
        }));
    }
}
