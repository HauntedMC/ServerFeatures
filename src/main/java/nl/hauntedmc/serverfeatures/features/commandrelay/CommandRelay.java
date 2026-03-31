package nl.hauntedmc.serverfeatures.features.commandrelay;

import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.commandrelay.command.CommandRelayCommand;
import nl.hauntedmc.serverfeatures.features.commandrelay.internal.EventBusHandler;
import nl.hauntedmc.serverfeatures.features.commandrelay.meta.Meta;

import java.util.List;
import java.util.Optional;

public class CommandRelay extends BukkitBaseFeature<Meta> {

    private EventBusHandler eventBusHandler;

    public CommandRelay(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("listening", false);
        defaults.put("sending", false);
        // whitelist of main command names (no leading slash), e.g. List.of("eco", "fly")
        defaults.put("command_whitelist", List.of());
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("commandrelay.usage", "&eGebruik: /commandrelay <targetServer> <command...>");
        messages.add("commandrelay.relayed", "&aCommand relayed naar {target}: {cmd}");
        return messages;
    }

    @Override
    public void initialize() {
        // Init Redis messaging
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

        // Create the handler
        this.eventBusHandler = new EventBusHandler(this, redisBus.get());

        // Fetch settings
        boolean listen = (Boolean) getConfigHandler().get("listening");
        boolean send = (Boolean) getConfigHandler().get("sending");
        String serverName = (String) getConfigHandler().getGlobalSetting("server_name");

        // If listening, subscribe to incoming commands for this server
        if (listen) {
            String channel = serverName + ".commandrelay.command";
            eventBusHandler.subscribe(channel);
            getLogger()
                    .info("CommandRelay: listening on Redis channel “" + channel + "”");
        }

        // If sending, register the /commandrelay command
        if (send) {
            getLifecycleManager()
                    .getCommandManager()
                    .registerFeatureCommand(new CommandRelayCommand(this));
            getLogger()
                    .info("CommandRelay: /commandrelay command registered");
        }
    }

    @Override
    public void disable() {
        if (eventBusHandler != null) {
            eventBusHandler.disable();
        }
    }

    public EventBusHandler getEventBusHandler() {
        return eventBusHandler;
    }
}
