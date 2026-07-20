package nl.hauntedmc.serverfeatures.features.chatlog.internal.services;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.serverfeatures.features.chatlog.ChatLog;
import nl.hauntedmc.serverfeatures.features.chatlog.entities.ChatMessageEntity;
import nl.hauntedmc.serverfeatures.features.chatlog.entities.ReportedChatMessageEntity;
import nl.hauntedmc.serverfeatures.framework.persistence.PlayerIdentityResolver;
import org.bukkit.entity.Player;
import org.hibernate.Session;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ChatLogService {

    private final ChatLog feature;
    private final PlayerIdentityResolver playerResolver;

    public ChatLogService(ChatLog feature) {
        this(feature, feature.getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for ChatLog.")));
    }

    ChatLogService(ChatLog feature, DataRegistryApi dataRegistry) {
        this.feature = feature;
        this.playerResolver = new PlayerIdentityResolver(dataRegistry);
    }

    ChatLogService(ChatLog feature, PlayerDirectory playerDirectory) {
        this.feature = feature;
        this.playerResolver = new PlayerIdentityResolver(playerDirectory);
    }

    /**
     * Logs a chat message into the chat_messages table.
     */
    public void addMessage(Player player, String rawMessage) {
        String serverName = (String) feature.getConfigHandler().getGlobalSetting("server_name");
        long timestamp = System.currentTimeMillis();
        java.util.UUID playerUuid = player.getUniqueId();
        playerResolver.whenReady(playerUuid).whenComplete((identity, throwable) -> {
            if (throwable != null) {
                feature.getLogger().warning("DataRegistry identity unavailable for chat log: " + throwable.getMessage());
                return;
            }
            if (identity == null || identity.isEmpty()) {
                return;
            }
            schedulePersist(serverName, timestamp, playerUuid, rawMessage);
        });
    }

    boolean addMessage(Session session, String serverName, long timestamp, Player player, String rawMessage) {
        PlayerIdentity playerIdentity = playerResolver.findActiveByUuid(player.getUniqueId()).orElse(null);

        if (playerIdentity == null) {
            return false;
        }

        ChatMessageEntity message = new ChatMessageEntity();
        message.setServer(serverName);
        message.setPlayerId(playerIdentity.playerId());
        message.setMessage(rawMessage);
        message.setTimestamp(timestamp);
        session.persist(message);
        return true;
    }

    private void schedulePersist(
            String serverName,
            long timestamp,
            java.util.UUID playerUuid,
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
                        PlayerIdentity playerIdentity = playerResolver.findActiveByUuid(playerUuid).orElse(null);
                        if (playerIdentity == null) {
                            return null;
                        }
                        ChatMessageEntity message = new ChatMessageEntity();
                        message.setServer(serverName);
                        message.setPlayerId(playerIdentity.playerId());
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
     * Counts messages for an active player, identified by their current username.
     */
    public int countMessages(String server, String playerName, Long start, Long end) {
        Long playerId = playerResolver.findActiveByUsername(playerName)
                .map(PlayerIdentity::playerId)
                .orElse(null);
        if (playerId == null) {
            return 0;
        }
        return feature.getOrmContext().runInTransaction(session -> {
            Long count = session.createQuery(
                    "SELECT COUNT(c) FROM ChatMessageEntity c WHERE c.server = :server "
                            + "AND c.playerId = :playerId AND c.timestamp BETWEEN :start AND :end",
                            Long.class)
                    .setParameter("server", server)
                    .setParameter("playerId", playerId)
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .getSingleResult();
            return count.intValue();
        });
    }

    /**
     * Creates a report by copying messages for active players into the reported-chat table.
     */
    public void createReport(String server, List<String> players, Long start, Long end, String reportId) {
        Set<Long> playerIds = players.stream()
                .map(playerResolver::findActiveByUsername)
                .flatMap(java.util.Optional::stream)
                .map(PlayerIdentity::playerId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (playerIds.isEmpty()) {
            return;
        }
        feature.getOrmContext().runInTransaction(session -> {
            List<ChatMessageEntity> messages = session.createQuery(
                            "SELECT c FROM ChatMessageEntity c WHERE c.server = :server "
                                    + "AND c.playerId IN :playerIds AND c.timestamp BETWEEN :start AND :end",
                            ChatMessageEntity.class)
                    .setParameter("server", server)
                    .setParameter("playerIds", playerIds)
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .getResultList();

            for (ChatMessageEntity msg : messages) {
                ReportedChatMessageEntity reported = new ReportedChatMessageEntity();
                reported.setServer(msg.getServer());
                reported.setPlayerId(msg.getPlayerId());
                reported.setMessage(msg.getMessage());
                reported.setTimestamp(msg.getTimestamp());
                reported.setReportId(reportId);
                session.persist(reported);
            }
            return null;
        });
    }
}
