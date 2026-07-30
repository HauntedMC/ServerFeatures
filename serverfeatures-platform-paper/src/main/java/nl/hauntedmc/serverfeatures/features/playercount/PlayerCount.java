package nl.hauntedmc.serverfeatures.features.playercount;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.playercount.internal.PlayerCountAPI;
import nl.hauntedmc.serverfeatures.features.playercount.internal.PlayerCountPlaceholder;
import nl.hauntedmc.serverfeatures.features.playercount.internal.PlayerCountSnapshotStore;
import nl.hauntedmc.serverfeatures.features.playercount.internal.messaging.EventBusHandler;
import nl.hauntedmc.serverfeatures.features.playercount.meta.Meta;

import java.util.Optional;

/**
 * Receives proxy player-count snapshots and exposes them locally and through PlaceholderAPI.
 */
public final class PlayerCount extends BukkitBaseFeature<Meta> {

    static final String DEFAULT_CHANNEL = "proxy.playercount.snapshot";
    static final int DEFAULT_STALE_AFTER_SECONDS = 10;
    static final String DEFAULT_PUBLISHER_ID = "proxy";

    private PlayerCountSnapshotStore store;
    private PlayerCountAPI api;
    private EventBusHandler eventBusHandler;
    private PlayerCountPlaceholder placeholder;

    public PlayerCount(FeatureContext<Meta> context) {
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
                    "Global setting 'server_name' is missing; local player-count placeholders use 'server'."
            );
        }
        int staleAfterSeconds = positiveIntSetting(
                "stale_after_seconds",
                DEFAULT_STALE_AFTER_SECONDS
        );
        String publisherId = textSetting("publisher_id", DEFAULT_PUBLISHER_ID);
        store = new PlayerCountSnapshotStore(
                serverName,
                staleAfterSeconds * 1_000L,
                publisherId
        );
        api = new PlayerCountAPI(store);
        getLifecycleManager().getApiManager().registerService(PlayerCountAPI.class, api);

        if (getPlugin().getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholder = new PlayerCountPlaceholder(api);
            placeholder.register();
        }

        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        Optional<MessagingDataAccess> redisBus = getLifecycleManager()
                .getDataManager()
                .registerRedisMessagingDataAccess("redis", "hauntedmc");
        if (redisBus.isEmpty()) {
            getLogger().warning(
                    "Redis messaging connection 'redis' is unavailable; player-count placeholders remain unavailable."
            );
            return;
        }

        String channel = textSetting("channel", DEFAULT_CHANNEL);
        eventBusHandler = new EventBusHandler(this, redisBus.get(), store);
        try {
            eventBusHandler.subscribe(channel);
        } catch (RuntimeException exception) {
            eventBusHandler = null;
            getLogger().warning(
                    "Could not subscribe to player-count snapshots on '" + channel + "': "
                            + rootMessage(exception)
            );
            return;
        }
        getLogger().info(
                "Receiving player-count snapshots from '" + channel + "' for local server '"
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

    public PlayerCountAPI getApi() {
        return api;
    }

    public PlayerCountSnapshotStore getStore() {
        return store;
    }

    public EventBusHandler getEventBusHandler() {
        return eventBusHandler;
    }

    private String textSetting(String key, String fallback) {
        String configured = getConfigHandler().get(key, String.class, fallback);
        if (configured == null || configured.isBlank()) {
            getLogger().warning("PlayerCount setting '" + key + "' is blank; using '" + fallback + "'.");
            return fallback;
        }
        return configured.trim();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private int positiveIntSetting(String key, int fallback) {
        Object configured = getConfigHandler().get(key);
        if (configured instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        getLogger().warning("PlayerCount setting '" + key + "' must be positive; using " + fallback + ".");
        return fallback;
    }
}
