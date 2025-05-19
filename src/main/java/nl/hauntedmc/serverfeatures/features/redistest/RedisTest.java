package nl.hauntedmc.serverfeatures.features.redistest;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.dataprovider.database.DatabaseProvider;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.MessageRegistry;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.redistest.internal.ChatMessage;
import nl.hauntedmc.serverfeatures.features.redistest.internal.EventBusHandler;
import nl.hauntedmc.serverfeatures.features.redistest.listener.ChatListener;
import nl.hauntedmc.serverfeatures.features.redistest.meta.Meta;

import java.util.Optional;

/**
 * RedisTest feature → implements global chat via your new MessagingDataAccess API.
 */
public class RedisTest extends BukkitBaseFeature<Meta> {

    private EventBusHandler eventBusHandler;

    public RedisTest(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
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
            getPlugin().getLogger().warning("RedisTest: no Redis provider available");
            return;
        }

        DatabaseProvider dbp = opt.get();
        MessagingDataAccess redisBus;
        try {
            redisBus = (MessagingDataAccess) dbp.getDataAccess();
        } catch (ClassCastException e) {
            getPlugin().getLogger().severe("RedisTest: provider is not a MessagingDataAccess");
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
