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
     * Slaat een geverifieerde command-executie op in de database.
     * "server" komt van de globale setting "server_name".
     *
     * @param source      CommandSender (speler, console, rcon, enz.)
     * @param fullCommand command zonder leading slash
     */
    public void logServerCommand(CommandSender source, String fullCommand) {
        final long timestamp = System.currentTimeMillis();
        final String serverName = (String) feature.getConfigHandler().getGlobalSetting("server_name");
        final String sourceLabel = source.getClass().getSimpleName().toLowerCase(Locale.ROOT);

        if (source instanceof Player player) {
            final java.util.UUID playerUuid = player.getUniqueId();
            playerResolver.whenReady(playerUuid).whenComplete((identity, throwable) -> {
                if (throwable != null) {
                    feature.getLogger().warning("DataRegistry identity unavailable for command log: "
                            + throwable.getMessage());
                    return;
                }
                if (identity == null || identity.isEmpty()) {
                    return;
                }
                schedulePersist(serverName, timestamp, playerUuid, sourceLabel, fullCommand);
            });
            return;
        }

        schedulePersist(serverName, timestamp, null, sourceLabel, fullCommand);
    }

    void logServerCommand(Session session, String serverName, long timestamp, CommandSender source, String fullCommand) {
        java.util.UUID playerUuid = source instanceof Player player ? player.getUniqueId() : null;
        String sourceLabel = source.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        logServerCommand(session, serverName, timestamp, playerUuid, sourceLabel, fullCommand);
    }

    void logServerCommand(
            Session session,
            String serverName,
            long timestamp,
            java.util.UUID playerUuid,
            String sourceLabel,
            String fullCommand
    ) {
        PlayerIdentity playerIdentity = playerUuid == null
                ? null
                : playerResolver.findActiveByUuid(playerUuid).orElse(null);

        CommandExecutionEntity entry = new CommandExecutionEntity();
        entry.setServer(serverName);
        entry.setPlayerId(playerIdentity == null ? null : playerIdentity.playerId());
        entry.setSource(sourceLabel);
        entry.setCommand(fullCommand);
        entry.setTimestamp(timestamp);

        session.persist(entry);
    }

    private void schedulePersist(
            String serverName,
            long timestamp,
            java.util.UUID playerUuid,
            String sourceLabel,
            String fullCommand
    ) {
        if (!feature.getPlugin().isEnabled()) {
            return;
        }
        try {
            feature.getLifecycleManager().getTaskManager().scheduleAsyncTask(() -> {
                if (!feature.getPlugin().isEnabled()) {
                    return;
                }
                feature.getOrmContext().runInTransaction(session -> {
                        logServerCommand(session, serverName, timestamp, playerUuid, sourceLabel, fullCommand);
                        return null;
                });
            });
        } catch (RuntimeException exception) {
            feature.getLogger().warning("Could not schedule command log write: " + exception.getMessage());
        }
    }

}
