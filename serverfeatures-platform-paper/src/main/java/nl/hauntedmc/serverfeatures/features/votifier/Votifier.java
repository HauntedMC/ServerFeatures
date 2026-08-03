package nl.hauntedmc.serverfeatures.features.votifier;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDatabaseProvider;
import nl.hauntedmc.dataprovider.database.messaging.durable.DurableMessagingDataAccess;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.votifier.internal.EventBusHandler;
import nl.hauntedmc.serverfeatures.features.votifier.meta.Meta;

import java.util.Locale;
import java.util.Optional;

public class Votifier extends BukkitBaseFeature<Meta> {

    private static final String DEFAULT_STREAM = "proxy.votifier.vote";
    private static final String DEFAULT_STREAM_PATTERN = "{channel}.{server}";
    private static final String DEFAULT_SERVER_NAME = "server";
    private static final String SERVER_NAME_PATTERN = "[A-Za-z0-9_.-]{1,64}";
    private static final String CONNECTION = "hauntedmc";

    private EventBusHandler eventBusHandler;

    public Votifier(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        cfg.put("channel", DEFAULT_STREAM);
        cfg.put("stream_pattern", DEFAULT_STREAM_PATTERN);
        cfg.put("consumer_group", "");
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());

        Optional<MessagingDatabaseProvider> redisProvider = getLifecycleManager()
                .getDataManager()
                .registerRedisMessagingProvider("redis", CONNECTION);
        if (redisProvider.isEmpty()) {
            throw new IllegalStateException(
                    "Redis messaging provider is not available for feature '" + getFeatureName() + "'."
            );
        }
        DurableMessagingDataAccess redisBus = redisProvider.get().getDurableDataAccess();

        String baseStream = requireStream(
                getConfigHandler().get("channel", String.class, DEFAULT_STREAM)
        );
        String streamPattern = requireStreamPattern(
                getConfigHandler().get("stream_pattern", String.class, DEFAULT_STREAM_PATTERN)
        );
        String serverName = requireServerName(getConfigHandler().getGlobalSetting(
                "server_name",
                String.class,
                DEFAULT_SERVER_NAME
        ));
        String stream = resolveDeliveryStream(baseStream, streamPattern, serverName);

        String configuredGroup = getConfigHandler().get("consumer_group", String.class, "");
        String consumerGroup = resolveConsumerGroup(configuredGroup, serverName);

        this.eventBusHandler = new EventBusHandler(this, redisBus);
        this.eventBusHandler.consume(stream, consumerGroup);

        getLogger().info(
                "Votifier backend=\"" + normalizeTargetServerName(serverName)
                        + "\", stream=\"" + stream
                        + "\", consumer_group=\"" + consumerGroup + "\"."
        );
    }

    @Override
    public void disable() {
        if (eventBusHandler != null) {
            eventBusHandler.disable();
            eventBusHandler = null;
        }
    }

    static String resolveDeliveryStream(
            String baseStream,
            String streamPattern,
            String serverName
    ) {
        return requireStreamPattern(streamPattern)
                .replace("{channel}", requireStream(baseStream))
                .replace("{server}", normalizeTargetServerName(serverName));
    }

    static String resolveConsumerGroup(String configuredGroup, String serverName) {
        String normalizedServerName = normalizeTargetServerName(serverName);
        if (configuredGroup != null && !configuredGroup.isBlank()) {
            return normalizeConsumerKey(configuredGroup, "serverfeatures.votifier.server");
        }
        return normalizeConsumerKey(
                "serverfeatures.votifier." + normalizedServerName,
                "serverfeatures.votifier.server"
        );
    }

    static String normalizeTargetServerName(String value) {
        String serverName = requireServerName(value);
        return serverName.toLowerCase(Locale.ROOT);
    }

    private static String requireServerName(String value) {
        String serverName = value == null ? "" : value.trim();
        if (serverName.isBlank()) {
            throw new IllegalArgumentException("server_name must not be blank");
        }
        if (!serverName.matches(SERVER_NAME_PATTERN)) {
            throw new IllegalArgumentException(
                    "server_name must match " + SERVER_NAME_PATTERN + " and identify a registered Velocity backend"
            );
        }
        if (DEFAULT_SERVER_NAME.equalsIgnoreCase(serverName)) {
            throw new IllegalStateException(
                    "Votifier requires a unique global 'server_name'; the default value \"server\" is not allowed."
            );
        }
        return serverName;
    }

    private static String requireStream(String value) {
        String stream = value == null ? "" : value.trim();
        if (stream.isBlank()) {
            throw new IllegalArgumentException("Votifier channel must not be blank");
        }
        return stream;
    }

    private static String requireStreamPattern(String value) {
        String pattern = value == null ? "" : value.trim();
        if (pattern.isBlank()) {
            throw new IllegalArgumentException("Votifier stream_pattern must not be blank");
        }
        if (!pattern.contains("{server}")) {
            throw new IllegalArgumentException("Votifier stream_pattern must contain {server}");
        }
        return pattern;
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
}
