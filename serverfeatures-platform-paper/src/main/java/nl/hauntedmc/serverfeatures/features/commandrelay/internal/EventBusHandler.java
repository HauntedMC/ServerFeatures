package nl.hauntedmc.serverfeatures.features.commandrelay.internal;

import nl.hauntedmc.dataprovider.database.messaging.durable.DurableDelivery;
import nl.hauntedmc.dataprovider.database.messaging.durable.DurableEvent;
import nl.hauntedmc.dataprovider.database.messaging.durable.DurableMessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.durable.DurableSubscription;
import nl.hauntedmc.dataprovider.database.messaging.durable.PublishedDurableEvent;
import nl.hauntedmc.proxyfeatures.contracts.messaging.CommandRelayMessage;
import nl.hauntedmc.serverfeatures.api.io.cache.CacheDirectory;
import nl.hauntedmc.serverfeatures.api.io.cache.CacheType;
import nl.hauntedmc.serverfeatures.api.io.cache.FileCacheStore;
import nl.hauntedmc.serverfeatures.api.util.type.CastUtils;
import nl.hauntedmc.serverfeatures.features.commandrelay.CommandRelay;
import nl.hauntedmc.serverfeatures.features.commandrelay.audit.CommandRelayAuditLogService;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class EventBusHandler {

    private static final long CLOSE_TIMEOUT_SECONDS = 5L;

    private final DurableMessagingDataAccess redisBus;
    private final CommandRelay feature;
    private final CommandRelayAuditLogService auditLogService;
    private final ProcessedCommandLedger processedCommands;
    private final Set<String> activeOperations = ConcurrentHashMap.newKeySet();
    private DurableSubscription subscription;

    public EventBusHandler(
            CommandRelay feature,
            DurableMessagingDataAccess redisBus,
            long processedCommandTtlMillis
    ) {
        this(
                feature,
                redisBus,
                createLedger(feature, processedCommandTtlMillis),
                feature.getAuditLogService()
        );
    }

    EventBusHandler(
            CommandRelay feature,
            DurableMessagingDataAccess redisBus,
            ProcessedCommandLedger processedCommands,
            CommandRelayAuditLogService auditLogService
    ) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.redisBus = Objects.requireNonNull(redisBus, "redisBus");
        this.processedCommands = Objects.requireNonNull(processedCommands, "processedCommands");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
    }

    /**
     * Consume the given durable Redis stream and handle incoming CommandRelayMessage deliveries.
     */
    public void consume(String stream, String consumerGroup) {
        String consumer = consumerGroup + "." + UUID.randomUUID();
        try {
            this.subscription = Objects.requireNonNull(
                    redisBus.consume(
                            stream,
                            consumerGroup,
                            consumer,
                            CommandRelayMessage.TYPE,
                            CommandRelayMessage.class,
                            delivery -> handleIncoming(stream, delivery)
                    ),
                    "Durable command relay subscription cannot be null."
            );
            this.subscription.completion().exceptionally(throwable -> {
                feature.getLogger().severe(
                        "CommandRelay: durable consumer stopped: " + rootMessage(throwable)
                );
                return null;
            });
        } catch (RuntimeException exception) {
            feature.getLogger().severe(
                    "CommandRelay: failed to consume “" + stream + "”: " + rootMessage(exception)
            );
            throw exception;
        }
    }

    private void handleIncoming(String stream, DurableDelivery<CommandRelayMessage> delivery) {
        String processingKey = delivery.event().processingKey();
        CommandRelayMessage message = delivery.event().payload();
        if (message == null) {
            feature.getLogger().warning("CommandRelay: discarded null durable payload.");
            auditLogService.logEvent("invalid_payload", stream, null, null, null, "message=null");
            acknowledge(delivery);
            return;
        }

        String operationId = normalize(message.getOperationId());
        String origin = normalize(message.getOriginServer());
        String full = normalize(message.getCommand());
        if (operationId == null || !operationId.equals(processingKey) || origin == null || full == null) {
            feature.getLogger().warning(
                    "CommandRelay: discarded invalid durable command " + processingKey + "."
            );
            auditLogService.logEvent(
                    "invalid_payload",
                    stream,
                    origin,
                    null,
                    full,
                    invalidDetails(operationId, processingKey, origin, full)
            );
            acknowledge(delivery);
            return;
        }

        if (processedCommands.isProcessed(processingKey)) {
            feature.getLogger().fine("CommandRelay: ignored completed replay " + processingKey + ".");
            auditLogService.logEvent(
                    "replay_ignored",
                    stream,
                    origin,
                    null,
                    full,
                    "operation_id=" + operationId
            );
            acknowledge(delivery);
            return;
        }
        if (!activeOperations.add(processingKey)) {
            return;
        }

        if (full.startsWith("/")) {
            full = full.substring(1).trim();
        }
        if (full.isBlank()) {
            auditLogService.logEvent(
                    "invalid_payload",
                    stream,
                    origin,
                    null,
                    null,
                    "missing=command_alias"
            );
            activeOperations.remove(processingKey);
            acknowledge(delivery);
            return;
        }
        String main = full.contains(" ")
                ? full.substring(0, full.indexOf(' '))
                : full;

        List<String> whitelist = CastUtils.safeCastToList(
                feature.getConfigHandler().get("command_whitelist"),
                String.class
        );
        Set<String> allowed = whitelist.stream()
                .filter(command -> command != null && !command.isBlank())
                .map(command -> command.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        if (!allowed.contains(main.toLowerCase(Locale.ROOT))) {
            feature.getLogger().warning(
                    "CommandRelay: received forbidden “" + main + "” from " + origin + " – ignoring"
            );
            auditLogService.logEvent("forbidden_command", stream, origin, main, full, null);
            activeOperations.remove(processingKey);
            acknowledge(delivery);
            return;
        }

        String sendingCommand = full;
        String commandAlias = main;
        try {
            feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
                try {
                    ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
                    boolean dispatched = Bukkit.getServer().dispatchCommand(console, sendingCommand);
                    feature.getLogger().info(
                            "CommandRelay: dispatched “/" + sendingCommand + "” from " + origin
                                    + ": success=" + dispatched
                    );
                    if (dispatched) {
                        auditLogService.logEvent(
                                "executed",
                                stream,
                                origin,
                                commandAlias,
                                sendingCommand,
                                null
                        );
                    } else {
                        auditLogService.logEvent(
                                "dispatch_rejected",
                                stream,
                                origin,
                                commandAlias,
                                sendingCommand,
                                "success=false"
                        );
                    }
                    persistAndAcknowledge(delivery, processingKey);
                } catch (RuntimeException exception) {
                    activeOperations.remove(processingKey);
                    feature.getLogger().warning(
                            "CommandRelay: dispatch failed for " + processingKey + ": "
                                    + rootMessage(exception)
                    );
                    auditLogService.logEvent(
                            "dispatch_error",
                            stream,
                            origin,
                            commandAlias,
                            sendingCommand,
                            rootMessage(exception)
                    );
                }
            });
        } catch (RuntimeException exception) {
            activeOperations.remove(processingKey);
            feature.getLogger().warning(
                    "CommandRelay: could not schedule " + processingKey + ": " + rootMessage(exception)
            );
            auditLogService.logEvent(
                    "dispatch_error",
                    stream,
                    origin,
                    commandAlias,
                    sendingCommand,
                    "schedule: " + rootMessage(exception)
            );
        }
    }

    /**
     * Stop the durable consumer when the feature is disabled.
     */
    public void disable() {
        DurableSubscription current = subscription;
        subscription = null;
        if (current == null) {
            return;
        }
        try {
            current.closeAsync().get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            feature.getLogger().warning(
                    "CommandRelay: interrupted while stopping the durable consumer."
            );
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            feature.getLogger().warning(
                    "CommandRelay: could not confirm durable consumer shutdown: "
                            + rootMessage(exception)
            );
        }
    }

    /**
     * Publish a command to a remote server with a retry-safe operation id.
     */
    public CompletableFuture<PublishedDurableEvent> publish(String stream, String command) {
        String configuredOrigin = feature.getConfigHandler().getGlobalSetting(
                "server_name",
                String.class,
                "server"
        );
        String origin = normalize(configuredOrigin);
        if (origin == null) {
            origin = "server";
        }
        CommandRelayMessage message = new CommandRelayMessage(command, origin);
        DurableEvent<CommandRelayMessage> event = new DurableEvent<>(
                message.getOperationId(),
                message.getOperationId(),
                message
        );

        CompletableFuture<PublishedDurableEvent> publication;
        try {
            publication = Objects.requireNonNull(
                    redisBus.publish(stream, event),
                    "Durable command relay publication future cannot be null."
            );
        } catch (RuntimeException exception) {
            publication = CompletableFuture.failedFuture(exception);
        }
        publication.exceptionally(throwable -> {
            feature.getLogger().severe(
                    "CommandRelay: failed to publish to “" + stream + "”: "
                            + rootMessage(throwable)
            );
            return null;
        });
        return publication;
    }

    private void persistAndAcknowledge(
            DurableDelivery<CommandRelayMessage> delivery,
            String processingKey
    ) {
        CompletableFuture<Void> persistence;
        try {
            persistence = Objects.requireNonNull(
                    feature.getLifecycleManager().getTaskManager()
                            .runAsync(() -> processedCommands.markProcessed(processingKey)),
                    "Processed-command persistence future cannot be null."
            );
        } catch (RuntimeException exception) {
            activeOperations.remove(processingKey);
            feature.getLogger().warning(
                    "CommandRelay: could not schedule completion persistence for " + processingKey + ": "
                            + rootMessage(exception)
            );
            return;
        }

        persistence.thenRun(() -> {
            activeOperations.remove(processingKey);
            acknowledge(delivery);
        }).exceptionally(throwable -> {
            activeOperations.remove(processingKey);
            feature.getLogger().warning(
                    "CommandRelay: could not persist completion for " + processingKey + ": "
                            + rootMessage(throwable)
            );
            return null;
        });
    }

    private void acknowledge(DurableDelivery<CommandRelayMessage> delivery) {
        CompletableFuture<Void> acknowledgement;
        try {
            acknowledgement = Objects.requireNonNull(
                    delivery.acknowledge(),
                    "Durable acknowledgement future cannot be null."
            );
        } catch (RuntimeException exception) {
            feature.getLogger().warning(
                    "CommandRelay: could not acknowledge " + delivery.event().processingKey() + ": "
                            + rootMessage(exception)
            );
            return;
        }

        acknowledgement.exceptionally(throwable -> {
            feature.getLogger().warning(
                    "CommandRelay: could not acknowledge " + delivery.event().processingKey() + ": "
                            + rootMessage(throwable)
            );
            return null;
        });
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String invalidDetails(
            String operationId,
            String processingKey,
            String origin,
            String command
    ) {
        if (operationId == null) {
            return "missing=operation_id";
        }
        if (!operationId.equals(processingKey)) {
            return "operation_id_mismatch";
        }
        if (origin == null && command == null) {
            return "missing=command,origin_server";
        }
        return origin == null ? "missing=origin_server" : "missing=command";
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static ProcessedCommandLedger createLedger(
            CommandRelay feature,
            long processedCommandTtlMillis
    ) {
        CacheDirectory cacheDirectory = feature.getLifecycleManager()
                .getCacheManager()
                .getCacheDirectory(feature.getFeatureName(), "durable-commandrelay");
        FileCacheStore store = (FileCacheStore) cacheDirectory.getStore("processed", CacheType.JSON);
        return new ProcessedCommandLedger(store, processedCommandTtlMillis);
    }
}
