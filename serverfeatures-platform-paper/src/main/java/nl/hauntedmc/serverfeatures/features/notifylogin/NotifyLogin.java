package nl.hauntedmc.serverfeatures.features.notifylogin;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.notifylogin.internal.ConnectionMessageSettings;
import nl.hauntedmc.serverfeatures.features.notifylogin.internal.NotificationHandler;
import nl.hauntedmc.serverfeatures.features.notifylogin.internal.NotifyLoginAPI;
import nl.hauntedmc.serverfeatures.features.notifylogin.listener.PlayerListener;
import nl.hauntedmc.serverfeatures.features.notifylogin.meta.Meta;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NotifyLogin extends BukkitBaseFeature<Meta> {

    private static final String LEGACY_SUPREME_PLUS_MESSAGE =
            "&3[Supreme&f+&3] {name} heeft de server gejoined!";
    private static final String DEFAULT_JOIN_MESSAGE =
            "<color:#aab2c9>[<color:#ffd79c>+<color:#aab2c9>] {name}";
    private static final String DEFAULT_QUIT_MESSAGE =
            "<color:#aab2c9>[<color:#ffd79c>-<color:#aab2c9>] {name}";
    private static final String SUPREME_PLUS_JOIN_MESSAGE =
            "<color:#aab2c9>[<color:#ffd79c>+<color:#aab2c9>]  "
                    + "<gradient:#3B8585:#3B8585:#2B9D9D:#2B9D9D:#43B1B1:#43B1B1:#44D6D6:#EAEAEA:#44D6D6>"
                    + "[Supreme+]</gradient> <color:#aab2c9>%serverfeatures_nickname%";
    private static final String SUPREME_PLUS_QUIT_MESSAGE =
            "<color:#aab2c9>[<color:#ffd79c>-<color:#aab2c9>]  "
                    + "<gradient:#3B8585:#3B8585:#2B9D9D:#2B9D9D:#43B1B1:#43B1B1:#44D6D6:#EAEAEA:#44D6D6>"
                    + "[Supreme+]</gradient> <color:#aab2c9>%serverfeatures_nickname%";

    private NotificationHandler notificationHandler;

    public NotifyLogin(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        Map<String, Object> supremePlus = new LinkedHashMap<>();
        supremePlus.put("priority", 100);
        supremePlus.put("permission", "serverfeatures.feature.notifylogin.supremeplus");
        supremePlus.put("join", "notifylogin.group.supremeplus.join");
        supremePlus.put("quit", "notifylogin.group.supremeplus.quit");

        Map<String, Object> permissionOverrides = new LinkedHashMap<>();
        permissionOverrides.put("supremeplus", supremePlus);

        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("announce_vanish_state_changes", true);
        defaults.put("default", Map.of(
                "join", "notifylogin.default.join",
                "quit", "notifylogin.default.quit"
        ));
        defaults.put("permission_overrides", permissionOverrides);
        defaults.put("player_overrides", Map.of());
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("notifylogin.default.join", DEFAULT_JOIN_MESSAGE);
        messages.add("notifylogin.default.quit", DEFAULT_QUIT_MESSAGE);
        messages.add("notifylogin.group.supremeplus.join", SUPREME_PLUS_JOIN_MESSAGE);
        messages.add("notifylogin.group.supremeplus.quit", SUPREME_PLUS_QUIT_MESSAGE);
        return messages;
    }

    @Override
    public void initialize() {
        getLocalizationHandler().migrateMessageKey(
                "notifylogin.supremeplus",
                "notifylogin.group.supremeplus.join",
                LEGACY_SUPREME_PLUS_MESSAGE,
                SUPREME_PLUS_JOIN_MESSAGE
        );
        ConnectionMessageSettings settings = ConnectionMessageSettings.from(
                getConfigHandler().node(),
                message -> getLogger().warning(message)
        );
        this.notificationHandler = new NotificationHandler(this, settings);
        getLifecycleManager().getApiManager().registerService(
                NotifyLoginAPI.class,
                new NotifyLoginAPI(notificationHandler)
        );
        getLifecycleManager().getListenerManager().registerListener(new PlayerListener(this));
    }

    @Override
    public void disable() {
        if (notificationHandler != null) {
            notificationHandler.close();
        }
    }

    public NotificationHandler getNotificationHandler() {
        return notificationHandler;
    }
}
