package nl.hauntedmc.serverfeatures.features.commandrelay.audit;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.serverfeatures.features.commandrelay.CommandRelay;
import org.hibernate.Session;

import java.util.Objects;

public final class CommandRelayAuditLogService {

    private final CommandRelay feature;
    private final ORMContext orm;

    public CommandRelayAuditLogService(CommandRelay feature, ORMContext orm) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.orm = orm;
    }

    public void logEvent(String eventType,
                         String relayChannel,
                         String originServer,
                         String commandAlias,
                         String commandText,
                         String details) {
        if (orm == null) {
            return;
        }

        long createdAt = System.currentTimeMillis();
        String normalizedEventType = normalize(eventType, 64);
        if (normalizedEventType == null) {
            normalizedEventType = "unknown";
        }
        String normalizedRelayChannel = normalize(relayChannel, 100);
        String normalizedOriginServer = normalize(originServer, 100);
        String normalizedCommandAlias = normalize(commandAlias, 64);
        String normalizedCommandText = normalize(commandText, 512);
        String normalizedDetails = normalize(details, 512);
        String persistedEventType = normalizedEventType;

        try {
            feature.getLifecycleManager().getTaskManager().scheduleAsyncTask(() -> {
                try {
                    orm.runInTransaction(session -> {
                        persist(
                                session,
                                persistedEventType,
                                normalizedRelayChannel,
                                normalizedOriginServer,
                                normalizedCommandAlias,
                                normalizedCommandText,
                                normalizedDetails,
                                createdAt
                        );
                        return null;
                    });
                } catch (RuntimeException exception) {
                    feature.getLogger().warning(
                            "Failed to persist command relay audit log: " + rootMessage(exception)
                    );
                }
            });
        } catch (RuntimeException exception) {
            feature.getLogger().warning(
                    "Could not schedule command relay audit log write: " + rootMessage(exception)
            );
        }
    }

    void persist(Session session,
                 String eventType,
                 String relayChannel,
                 String originServer,
                 String commandAlias,
                 String commandText,
                 String details,
                 long createdAt) {
        CommandRelayAuditLogEntity entry = new CommandRelayAuditLogEntity();
        entry.setEventType(eventType);
        entry.setRelayChannel(relayChannel);
        entry.setOriginServer(originServer);
        entry.setCommandAlias(commandAlias);
        entry.setCommandText(commandText);
        entry.setDetails(details);
        entry.setCreatedAt(createdAt);
        session.persist(entry);
    }

    static String normalize(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
