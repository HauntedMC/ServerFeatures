package nl.hauntedmc.serverfeatures.features.commandrelay.internal;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.proxyfeatures.contracts.messaging.CommandRelayMessage;
import nl.hauntedmc.serverfeatures.api.util.type.CastUtils;
import nl.hauntedmc.serverfeatures.features.commandrelay.CommandRelay;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class EventBusHandler {

    private static final long UNSUBSCRIBE_TIMEOUT_SECONDS = 5L;

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
                    CommandRelayMessage.TYPE,
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
        if (full.isBlank()) {
            return;
        }
        String main = full.contains(" ")
                ? full.substring(0, full.indexOf(' '))
                : full;

        // Validate against whitelist
        List<String> whitelist =
                CastUtils.safeCastToList(
                        feature.getConfigHandler().get("command_whitelist"),
                        String.class
                );

        String normalizedMain = main.toLowerCase(Locale.ROOT);
        if (whitelist.stream()
                .map(command -> command.toLowerCase(Locale.ROOT))
                .noneMatch(normalizedMain::equals)) {
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
        Subscription current = subscription;
        subscription = null;
        if (current == null) {
            return;
        }
        try {
            current.unsubscribe().get(UNSUBSCRIBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            feature.getLogger().warning("CommandRelay: interrupted while unsubscribing.");
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            feature.getLogger().warning(
                    "CommandRelay: could not confirm subscription shutdown: " + rootMessage(exception)
            );
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

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
