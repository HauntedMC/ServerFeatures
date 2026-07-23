package nl.hauntedmc.serverfeatures.features.votereward.internal;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class VoteHandler {

    private final VoteReward feature;
    private final PlayerIdentityResolver playerResolver;
    private final CacheDirectory playerCacheDirectory;
    private final int msgDelay;
    private final int startDelay;
    private final int interval;
    private final long cacheTtlMillis;
    private final List<String> whitelist;
    private final List<String> commands;

    public VoteHandler(VoteReward feature) {
        this.feature = feature;
        this.playerResolver = new PlayerIdentityResolver(feature.getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for VoteReward.")));
        this.playerCacheDirectory = feature.getPlayerCacheDir();
        this.msgDelay = (int) feature.getConfigHandler().get("join_message_delay");
        this.startDelay = (int) feature.getConfigHandler().get("rewards_start_delay");
        this.interval = (int) feature.getConfigHandler().get("reward_interval");
        this.cacheTtlMillis = ((Number) feature.getConfigHandler().get("cache_ttl_millis")).longValue();
        this.whitelist = CastUtils.safeCastToList(feature.getConfigHandler().get("vote_whitelist"), String.class);
        this.commands = CastUtils.safeCastToList(feature.getConfigHandler().get("rewards"), String.class);
    }

    /**
     * Entry point from either listener. Bukkit event callers invoke this on the main thread.
     */
    public void handleVote(IncomingVote vote) {
        String service = vote.serviceName();
        String suppliedUsername = vote.username() == null ? "" : vote.username().trim();

        if (!whitelist.contains(service)) {
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

            PlayerIdentity resolved = identity.get();
            feature.getLifecycleManager().getTaskManager().runAsync(() ->
                    queueOfflineVote(resolved.uuid().toString(), service)
            ).whenComplete((ignored, queueThrowable) -> {
                if (queueThrowable != null) {
                    feature.getLogger().warning("Could not queue offline vote for " + resolved.username() + ": "
                            + rootMessage(queueThrowable));
                    return;
                }
                scheduleMain(() -> broadcastVote(resolved.username()));
            });
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
        cache.put("vote_" + System.currentTimeMillis(), value);
    }

    public void processOfflineVotesOnJoin(Player player) {
        UUID playerUuid = player.getUniqueId();
        String legacyName = player.getName().toLowerCase(Locale.ROOT);

        feature.getLifecycleManager().getTaskManager().supplyAsync(
                () -> loadPendingVotes(playerUuid.toString(), legacyName)
        ).whenComplete((pendingVotes, throwable) -> {
            if (throwable != null) {
                feature.getLogger().warning("Could not load offline votes for " + playerUuid + ": "
                        + rootMessage(throwable));
                return;
            }
            if (pendingVotes == null || pendingVotes.isEmpty()) {
                return;
            }
            scheduleMain(() -> deliverPendingVotes(playerUuid, pendingVotes));
        });
    }

    private List<PendingVote> loadPendingVotes(String stableKey, String legacyName) {
        Map<String, PendingVote> pending = new LinkedHashMap<>();
        collectPendingVotes(cacheStore(stableKey), pending);
        if (!stableKey.equalsIgnoreCase(legacyName)) {
            collectPendingVotes(cacheStore(legacyName), pending);
        }
        return new ArrayList<>(pending.values());
    }

    private void collectPendingVotes(FileCacheStore store, Map<String, PendingVote> pending) {
        store.listAll().forEach((key, value) -> {
            if (value != null && !value.isExpired()) {
                pending.putIfAbsent(store.hashCode() + ":" + key, new PendingVote(store, key));
            } else if (value != null) {
                store.remove(key);
            }
        });
    }

    private void deliverPendingVotes(UUID playerUuid, List<PendingVote> pendingVotes) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        int count = pendingVotes.size();
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
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
                int delay = index * interval;
                feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
                    Player current = Bukkit.getPlayer(playerUuid);
                    if (current == null || !current.isOnline()) {
                        return;
                    }
                    processVote(current);
                    feature.getLifecycleManager().getTaskManager().scheduleAsyncTask(
                            () -> pendingVote.store().remove(pendingVote.key())
                    );
                }, BukkitTime.ticks(delay));
            }
        }, BukkitTime.ticks(msgDelay + startDelay));
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
