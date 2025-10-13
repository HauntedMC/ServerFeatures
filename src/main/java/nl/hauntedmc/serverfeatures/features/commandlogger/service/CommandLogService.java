package nl.hauntedmc.serverfeatures.features.commandlogger.service;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.serverfeatures.features.commandlogger.CommandLogger;
import nl.hauntedmc.serverfeatures.features.commandlogger.entity.CommandExecutionEntity;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public class CommandLogService {

    private final CommandLogger feature;

    public CommandLogService(CommandLogger feature) {
        this.feature = feature;
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
            PlayerEntity playerEntity = null;

            if (source instanceof Player p) {
                playerEntity = session.createQuery(
                                "SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class)
                        .setParameter("uuid", p.getUniqueId().toString())
                        .uniqueResult();

                if (playerEntity == null) {
                    playerEntity = new PlayerEntity();
                    playerEntity.setUuid(p.getUniqueId().toString());
                    playerEntity.setUsername(p.getName());
                    session.persist(playerEntity);
                } else if (!p.getName().equals(playerEntity.getUsername())) {
                    playerEntity.setUsername(p.getName());
                    session.merge(playerEntity);
                }
            }

            String sourceLabel = source.getClass().getSimpleName().toLowerCase(Locale.ROOT);

            CommandExecutionEntity entry = new CommandExecutionEntity();
            entry.setServer(serverName);
            entry.setPlayer(playerEntity);            // nullable when not a player
            entry.setSource(sourceLabel);             // class name of the source, lowercased
            entry.setCommand(fullCommand);            // full command as entered
            entry.setTimestamp(timestamp);            // epoch millis

            session.persist(entry);
            return null;
        });
    }
}
