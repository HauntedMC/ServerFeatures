package nl.hauntedmc.serverfeatures.features.commandlogger.service;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.serverfeatures.features.commandlogger.CommandLogger;
import nl.hauntedmc.serverfeatures.features.commandlogger.entity.CommandExecutionEntity;
import nl.hauntedmc.serverfeatures.framework.persistence.PlayerIdentityResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.hibernate.Session;

import java.util.Locale;

public class CommandLogService {

    private final CommandLogger feature;
    private final PlayerIdentityResolver playerResolver;

    public CommandLogService(CommandLogger feature) {
        this(feature, feature.getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for CommandLogger.")));
    }

    CommandLogService(CommandLogger feature, DataRegistryApi dataRegistry) {
        this.feature = feature;
        this.playerResolver = new PlayerIdentityResolver(dataRegistry);
    }

    CommandLogService(CommandLogger feature, PlayerDirectory playerDirectory) {
        this.feature = feature;
        this.playerResolver = new PlayerIdentityResolver(playerDirectory);
    }

    /**
     * Stores a verified command execution.
     */
    public void logServerCommand(CommandSender source, String fullCommand) {
        long timestamp = System.currentTimeMillis();
        String serverName = (String) feature.getConfigHandler().getGlobalSetting("server_name");
        String sourceLabel = source.getClass().getSimpleName().toLowerCase(Locale.ROOT);

        if (source instanceof Player player) {
            playerResolver.whenReady(player.getUniqueId()).whenComplete((identity, throwable) -> {
                if (throwable != null) {
                    feature.getLogger().warning("DataRegistry identity unavailable for command log: "
                            + rootMessage(throwable));
                    return;
                }
                if (identity == null || identity.isEmpty()) {
                    return;
                }
                schedulePersist(serverName, timestamp, identity.get().playerId(), sourceLabel, fullCommand);
            });
            return;
        }

        schedulePersist(serverName, timestamp, null, sourceLabel, fullCommand);
    }

    void logServerCommand(Session session, String serverName, long timestamp, CommandSender source, String fullCommand) {
        Long playerId = source instanceof Player player
                ? playerResolver.findActiveByUuid(player.getUniqueId()).map(PlayerIdentity::playerId).orElse(null)
                : null;
        String sourceLabel = source.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        logServerCommand(session, serverName, timestamp, playerId, sourceLabel, fullCommand);
    }

    void logServerCommand(
            Session session,
            String serverName,
            long timestamp,
            Long playerId,
            String sourceLabel,
            String fullCommand
    ) {
        CommandExecutionEntity entry = new CommandExecutionEntity();
        entry.setServer(serverName);
        entry.setPlayerId(playerId);
        entry.setSource(sourceLabel);
        entry.setCommand(fullCommand);
        entry.setTimestamp(timestamp);
        session.persist(entry);
    }

    private void schedulePersist(
            String serverName,
            long timestamp,
            Long playerId,
            String sourceLabel,
            String fullCommand
    ) {
        try {
            feature.getLifecycleManager().getTaskManager().scheduleAsyncTask(() ->
                    feature.getOrmContext().runInTransaction(session -> {
                        logServerCommand(session, serverName, timestamp, playerId, sourceLabel, fullCommand);
                        return null;
                    })
            );
        } catch (RuntimeException exception) {
            feature.getLogger().warning("Could not schedule command log write: " + rootMessage(exception));
        }
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
