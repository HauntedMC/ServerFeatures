package nl.hauntedmc.serverfeatures.features.chatlog.internal.services;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.serverfeatures.features.chatlog.ChatLog;
import nl.hauntedmc.serverfeatures.features.chatlog.entities.ChatMessageEntity;
import nl.hauntedmc.serverfeatures.features.chatlog.entities.ReportedChatMessageEntity;
import nl.hauntedmc.serverfeatures.framework.persistence.PlayerEntityResolver;
import org.bukkit.entity.Player;
import org.hibernate.Session;

import java.util.List;

public class ChatLogService {

    private final ChatLog feature;
    private final PlayerEntityResolver playerResolver;

    public ChatLogService(ChatLog feature) {
        this(feature, feature.getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for ChatLog.")));
    }

    ChatLogService(ChatLog feature, DataRegistry dataRegistry) {
        this.feature = feature;
        this.playerResolver = new PlayerEntityResolver(dataRegistry);
    }

    ChatLogService(ChatLog feature, PlayerRepository playerRepository) {
        this.feature = feature;
        this.playerResolver = new PlayerEntityResolver(playerRepository);
    }

    /**
     * Logs a chat message into the chat_messages table.
     */
    public void addMessage(Player player, String rawMessage) {
        String serverName = (String) feature.getConfigHandler().getGlobalSetting("server_name");
        long timestamp = System.currentTimeMillis();
        java.util.UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        playerResolver.whenReady(playerUuid).whenComplete((identity, throwable) -> {
            if (throwable != null) {
                feature.getLogger().warning("DataRegistry identity unavailable for chat log: " + throwable.getMessage());
                return;
            }
            schedulePersist(serverName, timestamp, playerUuid, playerName, rawMessage);
        });
    }

    boolean addMessage(Session session, String serverName, long timestamp, Player player, String rawMessage) {
        PlayerEntity playerEntity = playerResolver.resolveManaged(session, player.getUniqueId(), player.getName());

        if (playerEntity == null) {
            return false;
        }

        ChatMessageEntity message = new ChatMessageEntity();
        message.setServer(serverName);
        message.setPlayer(playerEntity);
        message.setMessage(rawMessage);
        message.setTimestamp(timestamp);
        session.persist(message);
        return true;
    }

    private void schedulePersist(
            String serverName,
            long timestamp,
            java.util.UUID playerUuid,
            String playerName,
            String rawMessage
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
                        PlayerEntity playerEntity = playerResolver.resolveManaged(session, playerUuid, playerName);
                        if (playerEntity == null) {
                            return null;
                        }
                        ChatMessageEntity message = new ChatMessageEntity();
                        message.setServer(serverName);
                        message.setPlayer(playerEntity);
                        message.setMessage(rawMessage);
                        message.setTimestamp(timestamp);
                        session.persist(message);
                        return null;
                });
            });
        } catch (RuntimeException exception) {
            feature.getLogger().warning("Could not schedule chat log write: " + exception.getMessage());
        }
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
