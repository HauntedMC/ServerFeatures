package nl.hauntedmc.serverfeatures.features.commandrelay.internal;

import nl.hauntedmc.commonlib.util.CastUtils;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.serverfeatures.features.commandrelay.CommandRelay;
import nl.hauntedmc.serverfeatures.features.commandrelay.internal.messaging.CommandRelayMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;

import java.util.List;

public class EventBusHandler {

    private final MessagingDataAccess redisBus;
    private final CommandRelay feature;
    private Subscription subscription;

    public EventBusHandler(CommandRelay feature, MessagingDataAccess redisBus) {
        this.feature = feature;
        this.redisBus = redisBus;
    }

    /**
     * Subscribe to the given Redis channel and handle incoming CommandRelayMessage.
     */
    public void subscribe(String channel) {
        try {
            this.subscription = redisBus.subscribe(
                    channel,
                    CommandRelayMessage.class,
                    this::handleIncoming
            );
        } catch (Exception ex) {
            feature.getLogger()
                    .severe("CommandRelay: failed to subscribe to “" + channel + "”");
        }
    }

    private void handleIncoming(CommandRelayMessage msg) {
        if (msg.getCommand() == null || msg.getOriginServer() == null) {
            return;
        }

        String origin = msg.getOriginServer();
        String full = msg.getCommand().trim();
        if (full.startsWith("/")) {
            full = full.substring(1);
        }
        String main = full.contains(" ")
                ? full.substring(0, full.indexOf(' '))
                : full;

        // Validate against whitelist
        List<String> whitelist =
                CastUtils.safeCastToList(
                        feature.getConfigHandler().getSetting("command_whitelist"),
                        String.class
                );

        if (!whitelist.stream().map(String::toLowerCase).toList()
                .contains(main.toLowerCase())) {
            feature.getLogger()
                    .warning("CommandRelay: received forbidden “" + main +
                            "” from " + origin + " – ignoring");
            return;
        }

        final String sendingCommand = full;
        // Execute the command in console in sync thread
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
            ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
            boolean dispatched = Bukkit.getServer().dispatchCommand(console, sendingCommand);
            feature.getLogger()
                    .info("CommandRelay: dispatched “/" + sendingCommand +
                            "” from " + origin + ": success=" + dispatched);
        });

    }

    /**
     * Unsubscribe when feature is disabled.
     */
    public void disable() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    /**
     * Publish a command to a remote server, attaching this server as origin.
     */
    public void publish(String channel, String command) {
        // grab our own server name
        String origin = (String) feature.getConfigHandler().getGlobalSetting("server_name");
        redisBus.publish(channel, new CommandRelayMessage(command, origin))
                .exceptionally(ex -> {
                    feature.getLogger()
                            .severe("CommandRelay: failed to publish to “" + channel + "”");
                    return null;
                });
    }
}
