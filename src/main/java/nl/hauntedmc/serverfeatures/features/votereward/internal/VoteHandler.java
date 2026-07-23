package nl.hauntedmc.serverfeatures.features.votereward.internal;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.player.PlayerNameHistoryEntry;
import nl.hauntedmc.serverfeatures.api.io.cache.CacheDirectory;
import nl.hauntedmc.serverfeatures.api.io.cache.CacheType;
import nl.hauntedmc.serverfeatures.api.io.cache.CacheValue;
import nl.hauntedmc.serverfeatures.api.io.cache.FileCacheStore;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.api.util.type.CastUtils;
import nl.hauntedmc.serverfeatures.features.votereward.VoteReward;
import nl.hauntedmc.serverfeatures.framework.persistence.PlayerIdentityResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class VoteHandler {

    private static final int LEGACY_NAME_HISTORY_LIMIT = 100;

    private final VoteReward feature;
    private final PlayerData playerData;
    private final PlayerIdentityResolver playerResolver;
    private final CacheDirectory playerCacheDirectory;
    private final Map<UUID, UUID> replayGenerations = new ConcurrentHashMap<>();
    private final int msgDelay;
    private final int startDelay;
    private final int interval;
    private final long cacheTtlMillis;
    private final List<String> whitelist;
    private final List<String> commands;

    public VoteHandler(VoteReward feature) {
        this.feature = feature;
        DataRegistryApi dataRegistry = feature.getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for VoteReward."));
        this.playerData = dataRegistry.players();
        this.playerResolver = new PlayerIdentityResolver(dataRegistry);
        this.playerCacheDirectory = feature.getPlayerCacheDir();
        this.msgDelay = (int) feature.getConfigHandler().get("join_message_delay");
        this.startDelay = (int) feature.getConfigHandler().get("rewards_start_delay");
        this.interval = (int) feature.getConfigHandler().get("reward_interval");
        this.cacheTtlMillis = ((Number) feature.getConfigHandler().get("cache_ttl_millis")).longValue();
        this.whitelist = CastUtils.safeCastToList(feature.getConfigHandler().get("vote_whitelist"), String.class);
        this.commands = CastUtils.safeCastToList(feature.getConfigHandler().get("rewards"), String.class);
    }

    /**
     * Entry point from either listener. All Bukkit work is normalized onto the main thread.
     */
    public void handleVote(IncomingVote vote) {
        if (!Bukkit.isPrimaryThread()) {
            scheduleMain(() -> handleVote(vote));
            return;
        }

        String service = vote.serviceName();
        String suppliedUsername = vote.username() == null ? "" : vote.username().trim();

        if (service == null || !whitelist.contains(service)) {
            feature.getLogger().warning("Rejected vote from unwhitelisted service: " + service);
            return;
        }
        if (suppliedUsername.isBlank()) {
            feature.getLogger().warning("Rejected vote without a player username from " + service);
            return;
        }

        feature.getLogger().info("Received valid vote from " + service + " for player " + suppliedUsername);
        Player onlinePlayer = Bukkit.getPlayerExact(suppliedUsername);
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            broadcastVote(onlinePlayer.getName());
            processVote(onlinePlayer);
            return;
        }

        playerResolver.findByUsername(suppliedUsername).whenComplete((identity, throwable) -> {
            if (throwable != null) {
                feature.getLogger().warning("Could not resolve offline vote identity for " + suppliedUsername + ": "
                        + rootMessage(throwable));
                return;
            }
            if (identity == null || identity.isEmpty()) {
                feature.getLogger().warning("Rejected vote for unknown player " + suppliedUsername
                        + "; no player_entity identity exists.");
                return;
            }
            scheduleMain(() -> processResolvedVote(identity.get(), service));
        });
    }

    private void processResolvedVote(PlayerIdentity identity, String service) {
        Player current = Bukkit.getPlayer(identity.uuid());
        if (current != null && current.isOnline()) {
            broadcastVote(current.getName());
            processVote(current);
            return;
        }

        feature.getLifecycleManager().getTaskManager().runAsync(() ->
                queueOfflineVote(identity.uuid().toString(), service)
        ).whenComplete((ignored, queueThrowable) -> {
            if (queueThrowable != null) {
                feature.getLogger().warning("Could not queue offline vote for " + identity.username() + ": "
                        + rootMessage(queueThrowable));
                return;
            }
            scheduleMain(() -> broadcastVote(identity.username()));
        });
    }

    private void broadcastVote(String name) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votereward.vote_broadcast")
                    .with("player", name)
                    .forAudience(player)
                    .build());
        }
    }

    private void processVote(Player player) {
        player.sendMessage(feature.getLocalizationHandler()
                .getMessage("votereward.vote_received")
                .with("player", player.getName())
                .forAudience(player)
                .build());

        for (String template : commands) {
            String command = template.replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private void queueOfflineVote(String cacheKey, String service) {
        FileCacheStore cache = cacheStore(cacheKey);
        CacheValue value = CacheValue.builder(cacheTtlMillis).with("service", service).build();
        String voteKey = "vote_" + System.currentTimeMillis() + "_" + UUID.randomUUID();
        cache.put(voteKey, value);
    }

    public void processOfflineVotesOnJoin(Player player) {
        UUID playerUuid = player.getUniqueId();
        String currentName = player.getName();
        UUID replayGeneration = UUID.randomUUID();
        replayGenerations.put(playerUuid, replayGeneration);

        resolveLegacyCacheNames(playerUuid, currentName)
                .thenCompose(legacyNames -> feature.getLifecycleManager().getTaskManager().supplyAsync(
                        () -> loadPendingVotes(playerUuid.toString(), legacyNames)
                ))
                .whenComplete((pendingVotes, throwable) -> {
                    if (throwable != null) {
                        replayGenerations.remove(playerUuid, replayGeneration);
                        feature.getLogger().warning("Could not load offline votes for " + playerUuid + ": "
                                + rootMessage(throwable));
                        return;
                    }
                    if (pendingVotes == null || pendingVotes.isEmpty()) {
                        replayGenerations.remove(playerUuid, replayGeneration);
                        return;
                    }
                    scheduleMain(() -> deliverPendingVotes(playerUuid, replayGeneration, pendingVotes));
                });
    }

    private CompletionStage<List<String>> resolveLegacyCacheNames(UUID playerUuid, String currentName) {
        List<String> fallback = normalizedNames(currentName, List.of());
        return playerResolver.whenReady(playerUuid)
                .thenCompose(identity -> {
                    if (identity == null || identity.isEmpty()) {
                        return CompletableFuture.completedFuture(fallback);
                    }
                    return playerData.findNameHistory(identity.get().playerId(), LEGACY_NAME_HISTORY_LIMIT)
                            .thenApply(history -> normalizedNames(currentName, history));
                })
                .exceptionally(throwable -> {
                    feature.getLogger().warning("Could not resolve legacy vote cache names for " + playerUuid + ": "
                            + rootMessage(throwable));
                    return fallback;
                });
    }

    private List<String> normalizedNames(String currentName, List<PlayerNameHistoryEntry> history) {
        Set<String> names = new LinkedHashSet<>();
        names.add(normalizeName(currentName));
        if (history != null) {
            for (PlayerNameHistoryEntry entry : history) {
                if (entry != null && entry.username() != null && !entry.username().isBlank()) {
                    names.add(normalizeName(entry.username()));
                }
            }
        }
        names.remove("");
        return List.copyOf(names);
    }

    private List<PendingVote> loadPendingVotes(String stableKey, List<String> legacyNames) {
        List<PendingVote> pending = new ArrayList<>();
        collectPendingVotes(cacheStore(stableKey), pending);
        for (String legacyName : legacyNames) {
            if (!stableKey.equalsIgnoreCase(legacyName)) {
                collectPendingVotes(cacheStore(legacyName), pending);
            }
        }
        return pending;
    }

    private void collectPendingVotes(FileCacheStore store, List<PendingVote> pending) {
        store.listAll().forEach((key, value) -> {
            if (value != null && !value.isExpired()) {
                pending.add(new PendingVote(store, key));
            } else if (value != null) {
                store.remove(key);
            }
        });
    }

    private void deliverPendingVotes(UUID playerUuid, UUID replayGeneration, List<PendingVote> pendingVotes) {
        if (!isCurrentReplay(playerUuid, replayGeneration)) {
            return;
        }
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            replayGenerations.remove(playerUuid, replayGeneration);
            return;
        }

        int count = pendingVotes.size();
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
            if (!isCurrentReplay(playerUuid, replayGeneration)) {
                return;
            }
            Player current = Bukkit.getPlayer(playerUuid);
            if (current != null && current.isOnline()) {
                current.sendMessage(feature.getLocalizationHandler()
                        .getMessage("votereward.offline_votes_retrieved")
                        .with("count", String.valueOf(count))
                        .forAudience(current)
                        .build());
            }
        }, BukkitTime.ticks(msgDelay));

        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
            for (int index = 0; index < pendingVotes.size(); index++) {
                PendingVote pendingVote = pendingVotes.get(index);
                boolean finalVote = index == pendingVotes.size() - 1;
                int delay = index * interval;
                feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() ->
                        deliverPendingVote(playerUuid, replayGeneration, pendingVote, finalVote),
                        BukkitTime.ticks(delay));
            }
        }, BukkitTime.ticks(msgDelay + startDelay));
    }

    private void deliverPendingVote(
            UUID playerUuid,
            UUID replayGeneration,
            PendingVote pendingVote,
            boolean finalVote
    ) {
        try {
            if (!isCurrentReplay(playerUuid, replayGeneration)) {
                return;
            }
            Player current = Bukkit.getPlayer(playerUuid);
            if (current == null || !current.isOnline()) {
                return;
            }
            processVote(current);
            feature.getLifecycleManager().getTaskManager().scheduleAsyncTask(() -> {
                try {
                    pendingVote.store().remove(pendingVote.key());
                } catch (RuntimeException exception) {
                    feature.getLogger().warning("Could not remove delivered offline vote " + pendingVote.key() + ": "
                            + rootMessage(exception));
                }
            });
        } finally {
            if (finalVote) {
                replayGenerations.remove(playerUuid, replayGeneration);
            }
        }
    }

    private boolean isCurrentReplay(UUID playerUuid, UUID replayGeneration) {
        return replayGeneration.equals(replayGenerations.get(playerUuid));
    }

    private FileCacheStore cacheStore(String key) {
        return (FileCacheStore) playerCacheDirectory.getStore(key, CacheType.JSON);
    }

    private void scheduleMain(Runnable task) {
        try {
            feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(task);
        } catch (RuntimeException exception) {
            feature.getLogger().warning("Could not schedule vote completion: " + rootMessage(exception));
        }
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private record PendingVote(FileCacheStore store, String key) {
    }
}
