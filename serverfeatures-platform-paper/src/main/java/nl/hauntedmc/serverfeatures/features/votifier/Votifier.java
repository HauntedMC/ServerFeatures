package nl.hauntedmc.serverfeatures.features.votifier;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDatabaseProvider;
import nl.hauntedmc.dataprovider.database.messaging.durable.DurableMessagingDataAccess;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.votifier.internal.EventBusHandler;
import nl.hauntedmc.serverfeatures.features.votifier.meta.Meta;

import java.util.Locale;
import java.util.Optional;

public class Votifier extends BukkitBaseFeature<Meta> {

    private static final String DEFAULT_STREAM = "proxy.votifier.vote";
    private static final String DEFAULT_SERVER_NAME = "server";
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
            throw new IllegalStateException("Redis messaging provider is not available for feature '" + getFeatureName() + "'.");
        }
        DurableMessagingDataAccess redisBus = redisProvider.get().getDurableDataAccess();

        String configuredChannel = getConfigHandler().get("channel", String.class, DEFAULT_STREAM);
        String stream = resolveStream(configuredChannel);

        if (configuredChannel == null || configuredChannel.isBlank()) {
            getLogger().warning("Configured Votifier stream is blank; falling back to \"" + DEFAULT_STREAM + "\".");
        }

        String configuredGroup = getConfigHandler().get("consumer_group", String.class, "");
        String serverName = getConfigHandler().getGlobalSetting(
                "server_name",
                String.class,
                DEFAULT_SERVER_NAME
        );
        String consumerGroup = resolveConsumerGroup(configuredGroup, serverName);
        if ((configuredGroup == null || configuredGroup.isBlank())
                && DEFAULT_SERVER_NAME.equalsIgnoreCase(serverName)) {
            getLogger().warning(
                    "Votifier is using the default durable consumer group. Configure a unique global "
                            + "'server_name' or feature 'consumer_group' for every backend that must receive votes."
            );
        }

        this.eventBusHandler = new EventBusHandler(this, redisBus);
        this.eventBusHandler.consume(stream, consumerGroup);
    }

    @Override
    public void disable() {
        if (eventBusHandler != null) {
            eventBusHandler.disable();
            eventBusHandler = null;
        }
    }

    static String resolveStream(String configuredChannel) {
        if (configuredChannel == null) {
            return DEFAULT_STREAM;
        }

        String channel = configuredChannel.trim();
        return channel.isEmpty() ? DEFAULT_STREAM : channel;
    }

    static String resolveConsumerGroup(String configuredGroup, String serverName) {
        if (configuredGroup != null && !configuredGroup.isBlank()) {
            return normalizeConsumerKey(configuredGroup, "serverfeatures.votifier.server");
        }
        String normalizedServer = normalizeConsumerKey(serverName, DEFAULT_SERVER_NAME);
        return normalizeConsumerKey(
                "serverfeatures.votifier." + normalizedServer,
                "serverfeatures.votifier.server"
        );
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
