package nl.hauntedmc.serverfeatures.features.commandrelay;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDatabaseProvider;
import nl.hauntedmc.dataprovider.database.messaging.durable.DurableMessagingDataAccess;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.commandrelay.audit.CommandRelayAuditLogEntity;
import nl.hauntedmc.serverfeatures.features.commandrelay.audit.CommandRelayAuditLogService;
import nl.hauntedmc.serverfeatures.features.commandrelay.command.CommandRelayCommand;
import nl.hauntedmc.serverfeatures.features.commandrelay.internal.EventBusHandler;
import nl.hauntedmc.serverfeatures.features.commandrelay.meta.Meta;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureDataManager;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class CommandRelay extends BukkitBaseFeature<Meta> {

    private static final String DEFAULT_SERVER_NAME = "server";
    private static final String DEFAULT_CONSUMER_GROUP = "serverfeatures.commandrelay.server";
    private static final long DEFAULT_PROCESSED_COMMAND_TTL_MILLIS = 691_200_000L;
    private static final String AUDIT_DATABASE_IDENTIFIER = "commandRelayAudit";
    private static final String AUDIT_DATABASE_CONNECTION = "system_data_rw";

    private EventBusHandler eventBusHandler;
    private CommandRelayAuditLogService auditLogService;

    public CommandRelay(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("listening", false);
        defaults.put("sending", false);
        defaults.put("consumer_group", "");
        defaults.put("processed_command_ttl_millis", DEFAULT_PROCESSED_COMMAND_TTL_MILLIS);
        // whitelist of main command names (no leading slash), e.g. List.of("eco", "fly")
        defaults.put("command_whitelist", List.of());
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("commandrelay.usage", "&eGebruik: /commandrelay <targetServer> <command...>");
        messages.add("commandrelay.relayed", "&aCommand relayed naar {target}: {cmd}");
        messages.add("commandrelay.relay_failed", "&cCommand kon niet naar {target} worden verstuurd.");
        return messages;
    }

    @Override
    public void initialize() {
        FeatureDataManager dataManager = getLifecycleManager().getDataManager();
        dataManager.initDataProvider(getFeatureName());

        ORMContext auditOrm = dataManager.registerConnection(
                        AUDIT_DATABASE_IDENTIFIER,
                        DatabaseType.MYSQL,
                        AUDIT_DATABASE_CONNECTION
                )
                .flatMap(ignored -> dataManager.createORMContext(
                        AUDIT_DATABASE_IDENTIFIER,
                        CommandRelayAuditLogEntity.class
                ))
                .orElse(null);
        if (auditOrm == null) {
            getLogger().warning(
                    "CommandRelay database audit logging is disabled because the system ORM context is unavailable."
            );
        }
        this.auditLogService = new CommandRelayAuditLogService(this, auditOrm);

        Optional<MessagingDatabaseProvider> redisProvider = dataManager.registerRedisMessagingProvider(
                "redis",
                "hauntedmc"
        );

        if (redisProvider.isEmpty()) {
            throw new IllegalStateException("Redis messaging provider is not available for feature '" + getFeatureName() + "'.");
        }
        DurableMessagingDataAccess redisBus = redisProvider.get().getDurableDataAccess();
        long processedCommandTtlMillis = positiveLongSetting(
                "processed_command_ttl_millis",
                DEFAULT_PROCESSED_COMMAND_TTL_MILLIS
        );

        // Create the handler
        this.eventBusHandler = new EventBusHandler(this, redisBus, processedCommandTtlMillis);

        // Fetch settings
        boolean listen = (Boolean) getConfigHandler().get("listening");
        boolean send = (Boolean) getConfigHandler().get("sending");
        String serverName = resolveServerName(getConfigHandler().getGlobalSetting(
                "server_name",
                String.class,
                DEFAULT_SERVER_NAME
        ));

        // If listening, subscribe to incoming commands for this server
        if (listen) {
            String channel = serverName + ".commandrelay.command";
            String configuredGroup = getConfigHandler().get("consumer_group", String.class, "");
            String consumerGroup = resolveConsumerGroup(configuredGroup, serverName);
            eventBusHandler.consume(channel, consumerGroup);
            getLogger()
                    .info("CommandRelay: consuming durable Redis stream “" + channel
                            + "” as group “" + consumerGroup + "”");
        }

        // If sending, register the /commandrelay command
        if (send) {
            getLifecycleManager()
                    .getCommandManager()
                    .registerFeatureCommand(new CommandRelayCommand(this));
            getLogger()
                    .info("CommandRelay: /commandrelay command registered");
        }
    }

    @Override
    public void disable() {
        if (eventBusHandler != null) {
            eventBusHandler.disable();
        }
    }

    public EventBusHandler getEventBusHandler() {
        return eventBusHandler;
    }

    public CommandRelayAuditLogService getAuditLogService() {
        return auditLogService;
    }

    static String resolveConsumerGroup(String configuredGroup, String serverName) {
        if (configuredGroup != null && !configuredGroup.isBlank()) {
            return normalizeConsumerKey(configuredGroup, DEFAULT_CONSUMER_GROUP);
        }
        return normalizeConsumerKey(
                "serverfeatures.commandrelay." + resolveServerName(serverName),
                DEFAULT_CONSUMER_GROUP
        );
    }

    static String resolveServerName(String serverName) {
        return normalizeConsumerKey(serverName, DEFAULT_SERVER_NAME);
    }

    private static String normalizeConsumerKey(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9_.:-]", "_");
        normalized = normalized.replaceAll("_+", "_");
        if (normalized.isBlank()) {
            normalized = fallback;
        }
        return normalized.substring(0, Math.min(normalized.length(), 150));
    }

    private long positiveLongSetting(String key, long fallback) {
        Object configured = getConfigHandler().get(key);
        if (configured instanceof Number number && number.longValue() > 0L) {
            return number.longValue();
        }
        getLogger().warning("CommandRelay setting '" + key + "' must be positive; using " + fallback + ".");
        return fallback;
    }
}
