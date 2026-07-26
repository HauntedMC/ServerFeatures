package nl.hauntedmc.serverfeatures.features.votifier;

import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.votifier.internal.EventBusHandler;
import nl.hauntedmc.serverfeatures.features.votifier.meta.Meta;

import java.util.Optional;

public class Votifier extends BukkitBaseFeature<Meta> {

    private static final String DEFAULT_CHANNEL = "proxy.votifier.vote";
    private static final String CONNECTION = "hauntedmc";

    private EventBusHandler eventBusHandler;

    public Votifier(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        cfg.put("channel", DEFAULT_CHANNEL);
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        // Init data provider and get Redis messaging access
        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());

        Optional<MessagingDataAccess> redisBus = getLifecycleManager()
                .getDataManager()
                .registerDataAccess("redis", DatabaseType.REDIS_MESSAGING, CONNECTION, MessagingDataAccess.class);

        if (redisBus.isEmpty()) {
            throw new IllegalStateException("Redis messaging provider is not available for feature '" + getFeatureName() + "'.");
        }

        String configuredChannel = getConfigHandler().get("channel", String.class, DEFAULT_CHANNEL);
        String channel = resolveChannel(configuredChannel);

        if (configuredChannel == null || configuredChannel.isBlank()) {
            getLogger().warning("Configured Votifier channel is blank; falling back to \"" + DEFAULT_CHANNEL + "\".");
        }

        this.eventBusHandler = new EventBusHandler(this, redisBus.get());
        this.eventBusHandler.subscribe(channel);
    }

    @Override
    public void disable() {
        if (eventBusHandler != null) {
            eventBusHandler.disable();
            eventBusHandler = null;
        }
    }

    static String resolveChannel(String configuredChannel) {
        if (configuredChannel == null) {
            return DEFAULT_CHANNEL;
        }

        String channel = configuredChannel.trim();
        return channel.isEmpty() ? DEFAULT_CHANNEL : channel;
    }
}
