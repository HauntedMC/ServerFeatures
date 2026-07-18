package nl.hauntedmc.serverfeatures.features.commandlogger.service;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.serverfeatures.features.commandlogger.CommandLogger;
import nl.hauntedmc.serverfeatures.features.commandlogger.entity.CommandExecutionEntity;
import nl.hauntedmc.serverfeatures.framework.persistence.PlayerEntityResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.hibernate.Session;

import java.util.Locale;

public class CommandLogService {

    private final CommandLogger feature;
    private final PlayerEntityResolver playerResolver;

    public CommandLogService(CommandLogger feature) {
        this(feature, feature.getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for CommandLogger."))
                .getPlayerRepository());
    }

    CommandLogService(CommandLogger feature, PlayerRepository playerRepository) {
        this.feature = feature;
        this.playerResolver = new PlayerEntityResolver(playerRepository);
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

        feature.getOrmContext().runInTransaction(session -> {
            logServerCommand(session, serverName, timestamp, source, fullCommand);
            return null;
        });
    }

    void logServerCommand(Session session, String serverName, long timestamp, CommandSender source, String fullCommand) {
        PlayerEntity playerEntity = null;

        if (source instanceof Player player) {
            playerEntity = resolveExistingPlayerEntity(session, player.getUniqueId().toString(), player.getName());
        }

        String sourceLabel = source.getClass().getSimpleName().toLowerCase(Locale.ROOT);

        CommandExecutionEntity entry = new CommandExecutionEntity();
        entry.setServer(serverName);
        entry.setPlayer(playerEntity);
        entry.setSource(sourceLabel);
        entry.setCommand(fullCommand);
        entry.setTimestamp(timestamp);

        session.persist(entry);
    }

    private PlayerEntity resolveExistingPlayerEntity(Session session, String uuid, String username) {
        return playerResolver.resolveManaged(session, java.util.UUID.fromString(uuid), username);
    }
}
