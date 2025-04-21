package nl.hauntedmc.serverfeatures.features.redistest;

import nl.hauntedmc.dataprovider.database.DatabaseProvider;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.MessageRegistry;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BaseFeature;
import nl.hauntedmc.serverfeatures.features.redistest.internal.ChatMessage;
import nl.hauntedmc.serverfeatures.features.redistest.internal.EventBusHandler;
import nl.hauntedmc.serverfeatures.features.redistest.listener.ChatListener;
import nl.hauntedmc.serverfeatures.features.redistest.meta.Meta;
import nl.hauntedmc.serverfeatures.localization.MessageMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * RedisTest feature → implements global chat via your new MessagingDataAccess API.
 */
public class RedisTest extends BaseFeature<Meta> {


    private final ServerFeatures plugin;

    private EventBusHandler eventBusHandler;

    public RedisTest(ServerFeatures plugin) {
        super(plugin, new Meta());
        this.plugin = plugin;
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        defaults.put("server", "");
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        getLifecycleManager()
                .getDataManager()
                .initDataProvider(getFeatureName());

        Optional<DatabaseProvider> opt = getLifecycleManager()
                .getDataManager()
                .registerConnection(
                        "redis",
                        DatabaseType.REDIS_MESSAGING,
                        "default"
                );

        if (opt.isEmpty()) {
            plugin.getLogger().warning("RedisTest: no Redis provider available");
            return;
        }

        DatabaseProvider dbp = opt.get();
        MessagingDataAccess redisBus;
        try {
            redisBus = (MessagingDataAccess) dbp.getDataAccess();
        } catch (ClassCastException e) {
            plugin.getLogger().severe("RedisTest: provider is not a MessagingDataAccess");
            return;
        }

        MessageRegistry.register("chat", ChatMessage.class);

        eventBusHandler = new EventBusHandler(this, redisBus);
        eventBusHandler.subscribeChannel("mineserver.global");

        getLifecycleManager()
                .getListenerManager()
                .registerListener(new ChatListener(this));
    }

    @Override
    public void disable() {
        eventBusHandler.disable();
    }

    public EventBusHandler getEventBusHandler() {
        return eventBusHandler;
    }

}
