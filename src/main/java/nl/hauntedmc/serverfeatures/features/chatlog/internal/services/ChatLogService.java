package nl.hauntedmc.serverfeatures.features.chatlog.internal.services;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.serverfeatures.features.chatlog.ChatLog;
import nl.hauntedmc.serverfeatures.features.chatlog.entities.ChatMessageEntity;
import nl.hauntedmc.serverfeatures.features.chatlog.entities.ReportedChatMessageEntity;
import org.bukkit.entity.Player;

import java.util.List;

public class ChatLogService {

    private final ChatLog chatLog;

    public ChatLogService(ChatLog chatLog) {
        this.chatLog = chatLog;
    }

    /**
     * Logs a chat message into the chat_messages table.
     */
    public void addMessage(Player player, String rawMessage) {
        String serverName = (String) chatLog.getConfigHandler().getSetting("server");
        long timestamp = System.currentTimeMillis();

        chatLog.getOrmContext().runInTransaction(session -> {
            // Retrieve the persistent PlayerEntity using the player's UUID.
            PlayerEntity playerEntity = session.createQuery(
                            "SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class)
                    .setParameter("uuid", player.getUniqueId().toString())
                    .uniqueResult();

            // If not found, create and persist a new PlayerEntity.
            if (playerEntity == null) {
                playerEntity = new PlayerEntity();
                playerEntity.setUuid(player.getUniqueId().toString());
                playerEntity.setUsername(player.getName());
                session.persist(playerEntity);
            }

            ChatMessageEntity message = new ChatMessageEntity();
            message.setServer(serverName);
            message.setPlayer(playerEntity);
            message.setMessage(rawMessage);
            message.setTimestamp(timestamp);
            session.persist(message);
            return null;
        });
    }

    /**
     * Counts the number of chat messages for a given server and player between two timestamps.
     */
    public int countMessages(String server, String playerName, Long start, Long end) {
        return chatLog.getOrmContext().runInTransaction(session -> {
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
        chatLog.getOrmContext().runInTransaction(session -> {
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
