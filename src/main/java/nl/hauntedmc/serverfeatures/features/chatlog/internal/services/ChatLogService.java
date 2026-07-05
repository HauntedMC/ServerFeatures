package nl.hauntedmc.serverfeatures.features.chatlog.internal.services;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.serverfeatures.features.chatlog.ChatLog;
import nl.hauntedmc.serverfeatures.features.chatlog.entities.ChatMessageEntity;
import nl.hauntedmc.serverfeatures.features.chatlog.entities.ReportedChatMessageEntity;
import org.bukkit.entity.Player;
import org.hibernate.Session;

import java.util.List;

public class ChatLogService {

    private final ChatLog feature;

    public ChatLogService(ChatLog feature) {
        this.feature = feature;
    }

    /**
     * Logs a chat message into the chat_messages table.
     */
    public void addMessage(Player player, String rawMessage) {
        String serverName = (String) feature.getConfigHandler().getGlobalSetting("server_name");
        long timestamp = System.currentTimeMillis();

        feature.getOrmContext().runInTransaction(session -> {
            addMessage(session, serverName, timestamp, player, rawMessage);
            return null;
        });
    }

    boolean addMessage(Session session, String serverName, long timestamp, Player player, String rawMessage) {
        PlayerEntity playerEntity = session.createQuery(
                        "SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class)
                .setParameter("uuid", player.getUniqueId().toString())
                .uniqueResult();

        if (playerEntity == null) {
            return false;
        }

        if (!player.getName().equals(playerEntity.getUsername())) {
            playerEntity.setUsername(player.getName());
            session.merge(playerEntity);
        }

        ChatMessageEntity message = new ChatMessageEntity();
        message.setServer(serverName);
        message.setPlayer(playerEntity);
        message.setMessage(rawMessage);
        message.setTimestamp(timestamp);
        session.persist(message);
        return true;
    }

    /**
     * Counts the number of chat messages for a given server and player between two timestamps.
     */
    public int countMessages(String server, String playerName, Long start, Long end) {
        return feature.getOrmContext().runInTransaction(session -> {
            Long count = session.createQuery(
                            "SELECT COUNT(c) FROM ChatMessageEntity c WHERE c.server = :server AND c.player.username = :username AND c.timestamp BETWEEN :start AND :end",
                            Long.class)
                    .setParameter("server", server)
                    .setParameter("username", playerName)
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .getSingleResult();
            return count.intValue();
        });
    }

    /**
     * Creates a report by copying chat messages into the reported_chat_messages table.
     */
    public void createReport(String server, List<String> players, Long start, Long end, String reportId) {
        feature.getOrmContext().runInTransaction(session -> {
            for (String username : players) {
                List<ChatMessageEntity> messages = session.createQuery(
                                "SELECT c FROM ChatMessageEntity c WHERE c.server = :server AND c.player.username = :username AND c.timestamp BETWEEN :start AND :end",
                                ChatMessageEntity.class)
                        .setParameter("server", server)
                        .setParameter("username", username)
                        .setParameter("start", start)
                        .setParameter("end", end)
                        .getResultList();

                for (ChatMessageEntity msg : messages) {
                    ReportedChatMessageEntity reported = new ReportedChatMessageEntity();
                    reported.setServer(msg.getServer());
                    reported.setPlayer(msg.getPlayer());
                    reported.setMessage(msg.getMessage());
                    reported.setTimestamp(msg.getTimestamp());
                    reported.setReportId(reportId);
                    session.persist(reported);
                }
            }
            return null;
        });
    }
}
