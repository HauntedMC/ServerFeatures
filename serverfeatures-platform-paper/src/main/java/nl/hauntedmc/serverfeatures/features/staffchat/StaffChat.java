package nl.hauntedmc.serverfeatures.features.staffchat;

import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.staffchat.internal.ChatChannelHandler;
import nl.hauntedmc.serverfeatures.features.staffchat.internal.messaging.EventBusHandler;
import nl.hauntedmc.serverfeatures.features.staffchat.listener.ChatListener;
import nl.hauntedmc.serverfeatures.features.staffchat.meta.Meta;

import java.util.Optional;

public class StaffChat extends BukkitBaseFeature<Meta> {

    private ChatChannelHandler chatChannelHandler;
    private EventBusHandler eventBusHandler;

    public StaffChat(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("staff_prefix", "!");
        defaults.put("team_prefix", "@");
        defaults.put("admin_prefix", "#");
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

        Optional<MessagingDataAccess> redisBus = getLifecycleManager()
                .getDataManager()
                .registerDataAccess(
                        "redis",
                        DatabaseType.REDIS_MESSAGING,
                        "hauntedmc",
                        MessagingDataAccess.class
                );

        if (redisBus.isEmpty()) {
            throw new IllegalStateException("Redis messaging provider is not available for feature '" + getFeatureName() + "'.");
        }

        eventBusHandler = new EventBusHandler(this, redisBus.get());

        this.chatChannelHandler = new ChatChannelHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new ChatListener(this));
    }

    @Override
    public void disable() {
    }

    public ChatChannelHandler getChatChannelHandler() {
        return chatChannelHandler;
    }

    public EventBusHandler getEventBusHandler() {
        return eventBusHandler;
    }
}
