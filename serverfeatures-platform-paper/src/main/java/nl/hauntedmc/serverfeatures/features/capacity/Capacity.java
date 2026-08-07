package nl.hauntedmc.serverfeatures.features.capacity;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.capacity.internal.CapacityAPI;
import nl.hauntedmc.serverfeatures.features.capacity.internal.CapacityPlaceholder;
import nl.hauntedmc.serverfeatures.features.capacity.internal.CapacitySnapshotStore;
import nl.hauntedmc.serverfeatures.features.capacity.internal.messaging.EventBusHandler;
import nl.hauntedmc.serverfeatures.features.capacity.meta.Meta;

import java.util.Optional;

/** Receives authoritative proxy Capacity snapshots and exposes them through PlaceholderAPI. */
public final class Capacity extends BukkitBaseFeature<Meta> {

    static final String DEFAULT_CHANNEL = "proxy.capacity.snapshot";
    static final int DEFAULT_STALE_AFTER_SECONDS = 10;
    static final String DEFAULT_PUBLISHER_ID = "proxy";

    private CapacitySnapshotStore store;
    private CapacityAPI api;
    private EventBusHandler eventBusHandler;
    private CapacityPlaceholder placeholder;

    public Capacity(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("channel", DEFAULT_CHANNEL);
        defaults.put("stale_after_seconds", DEFAULT_STALE_AFTER_SECONDS);
        defaults.put("publisher_id", DEFAULT_PUBLISHER_ID);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        String serverName = getConfigHandler().getGlobalSetting(
                "server_name",
                String.class,
                "server"
        );
        if (serverName == null || serverName.isBlank()) {
            serverName = "server";
            getLogger().warning(
                    "Global setting 'server_name' is missing; local Capacity placeholders use 'server'."
            );
        }

        int staleAfterSeconds = positiveIntSetting(
                "stale_after_seconds",
                DEFAULT_STALE_AFTER_SECONDS
        );
        String publisherId = textSetting("publisher_id", DEFAULT_PUBLISHER_ID);
        store = new CapacitySnapshotStore(
                serverName,
                staleAfterSeconds * 1_000L,
                publisherId
        );
        api = new CapacityAPI(store);

        if (getPlugin().getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            CapacityPlaceholder candidate = new CapacityPlaceholder(api);
            if (candidate.register()) {
                placeholder = candidate;
            } else {
                getLogger().warning(
                        "Could not register the 'capacity' PlaceholderAPI expansion; "
                                + "another expansion may already use that identifier."
                );
            }
        }

        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        Optional<MessagingDataAccess> redisBus = getLifecycleManager()
                .getDataManager()
                .registerRedisMessagingDataAccess("redis", "hauntedmc");
        if (redisBus.isEmpty()) {
            getLogger().warning(
                    "Redis messaging connection 'redis' is unavailable; Capacity placeholders "
                            + "remain unavailable."
            );
            return;
        }

        String channel = textSetting("channel", DEFAULT_CHANNEL);
        EventBusHandler handler = new EventBusHandler(this, redisBus.get(), store);
        try {
            handler.subscribe(channel);
            eventBusHandler = handler;
        } catch (RuntimeException exception) {
            handler.disable();
            getLogger().warning(
                    "Could not subscribe to Capacity snapshots on '" + channel + "': "
                            + rootMessage(exception)
            );
            return;
        }

        getLogger().info(
                "Receiving Capacity snapshots from '" + channel + "' for local server '"
                        + store.getLocalServerName() + "' from publisher '"
                        + store.getExpectedPublisherId() + "'."
        );
    }

    @Override
    public void disable() {
        if (eventBusHandler != null) {
            eventBusHandler.disable();
            eventBusHandler = null;
        }
        if (placeholder != null) {
            placeholder.unregister();
            placeholder = null;
        }
        if (store != null) {
            store.clear();
        }
    }

    public CapacityAPI getApi() {
        return api;
    }

    private String textSetting(String key, String fallback) {
        String configured = getConfigHandler().get(key, String.class, fallback);
        if (configured == null || configured.isBlank()) {
            getLogger().warning(
                    "Capacity setting '" + key + "' is blank; using '" + fallback + "'."
            );
            return fallback;
        }
        return configured.trim();
    }

    private int positiveIntSetting(String key, int fallback) {
        Object configured = getConfigHandler().get(key);
        if (configured instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        getLogger().warning(
                "Capacity setting '" + key + "' must be positive; using " + fallback + "."
        );
        return fallback;
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
