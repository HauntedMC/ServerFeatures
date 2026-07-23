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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
                feature.getLogger().warning("DataRegistry identity unavailable for chat log: "
                        + rootMessage(throwable));
                return;
            }
            if (identity == null || identity.isEmpty()) {
                return;
            }
            schedulePersist(serverName, timestamp, identity.get().playerId(), rawMessage);
        });
    }

    boolean addMessage(Session session, String serverName, long timestamp, Player player, String rawMessage) {
        return playerResolver.findActiveByUuid(player.getUniqueId())
                .map(identity -> addMessage(session, serverName, timestamp, identity.playerId(), rawMessage))
                .orElse(false);
    }

    boolean addMessage(Session session, String serverName, long timestamp, long playerId, String rawMessage) {
        if (playerId <= 0L) {
            return false;
        }

        ChatMessageEntity message = new ChatMessageEntity();
        message.setServer(serverName);
        message.setPlayerId(playerId);
        message.setMessage(rawMessage);
        message.setTimestamp(timestamp);
        session.persist(message);
        return true;
    }

    private void schedulePersist(String serverName, long timestamp, long playerId, String rawMessage) {
        try {
            feature.getLifecycleManager().getTaskManager().scheduleAsyncTask(() -> {
                if (!feature.getPlugin().isEnabled()) {
                    return;
                }
                feature.getOrmContext().runInTransaction(session -> {
                    addMessage(session, serverName, timestamp, playerId, rawMessage);
                    return null;
                });
            });
        } catch (RuntimeException exception) {
            feature.getLogger().warning("Could not schedule chat log write: " + rootMessage(exception));
        }
    }

    /**
     * Counts messages for a known player, resolving persisted identities when the player is offline.
     */
    public CompletionStage<Integer> countMessages(String server, String playerName, Long start, Long end) {
        return playerResolver.findByUsername(playerName)
                .thenCompose(identity -> identity
                        .map(value -> feature.getLifecycleManager().getTaskManager().supplyAsync(
                                () -> countMessagesByPlayerId(server, value.playerId(), start, end)
                        ))
                        .orElseGet(() -> CompletableFuture.completedFuture(0)));
    }

    /**
     * Creates a report for known players, including identities that are not active on this backend.
     */
    public CompletionStage<Void> createReport(
            String server,
            List<String> players,
            Long start,
            Long end,
            String reportId
    ) {
        return resolvePlayerIds(players).thenCompose(playerIds -> {
            if (playerIds.isEmpty()) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("No known player identities were resolved for the report.")
                );
            }
            return feature.getLifecycleManager().getTaskManager().runAsync(
                    () -> createReportByPlayerIds(server, playerIds, start, end, reportId)
            );
        });
    }

    private CompletionStage<Set<Long>> resolvePlayerIds(List<String> players) {
        List<CompletableFuture<Optional<Long>>> lookups = players.stream()
                .filter(player -> player != null && !player.isBlank())
                .map(String::trim)
                .distinct()
                .map(playerResolver::findByUsername)
                .map(stage -> stage.thenApply(identity -> identity.map(PlayerIdentity::playerId)).toCompletableFuture())
                .toList();

        if (lookups.isEmpty()) {
            return CompletableFuture.completedFuture(Set.of());
        }

        return CompletableFuture.allOf(lookups.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> lookups.stream()
                        .map(CompletableFuture::join)
                        .flatMap(Optional::stream)
                        .filter(playerId -> playerId > 0L)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    private int countMessagesByPlayerId(String server, long playerId, Long start, Long end) {
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

    private void createReportByPlayerIds(
            String server,
            Set<Long> playerIds,
            Long start,
            Long end,
            String reportId
    ) {
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

            for (ChatMessageEntity message : messages) {
                ReportedChatMessageEntity reported = new ReportedChatMessageEntity();
                reported.setServer(message.getServer());
                reported.setPlayerId(message.getPlayerId());
                reported.setMessage(message.getMessage());
                reported.setTimestamp(message.getTimestamp());
                reported.setReportId(reportId);
                session.persist(reported);
            }
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
