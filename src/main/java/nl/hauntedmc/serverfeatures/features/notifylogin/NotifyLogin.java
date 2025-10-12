package nl.hauntedmc.serverfeatures.features.notifylogin;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.notifylogin.internal.NotificationHandler;
import nl.hauntedmc.serverfeatures.features.notifylogin.listener.PlayerListener;
import nl.hauntedmc.serverfeatures.features.notifylogin.meta.Meta;

public class NotifyLogin extends BukkitBaseFeature<Meta> {

    private NotificationHandler notificationHandler;

    public NotifyLogin(ServerFeatures plugin) {
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
        MessageMap messages = new MessageMap();
        messages.add("notifylogin.supremeplus", "&3[Supreme&f+&3] {name} heeft de server gejoined!");
        return messages;
    }


    @Override
    public void initialize() {
        this.notificationHandler = new NotificationHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new PlayerListener(this));
    }

    @Override
    public void disable() {
    }

    public NotificationHandler getNotificationHandler() {
        return notificationHandler;
    }

}