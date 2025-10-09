package nl.hauntedmc.serverfeatures.features.votereward.internal;

import nl.hauntedmc.commonlib.util.CastUtils;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import nl.hauntedmc.serverfeatures.features.votereward.VoteReward;
import nl.hauntedmc.serverfeatures.internal.cache.*;

import java.util.*;

public class VoteHandler {

    private final VoteReward feature;
    private final int msgDelay;
    private final int startDelay;
    private final int interval;
    private final List<String> whitelist;
    private final List<String> commands;

    public VoteHandler(VoteReward feature) {
        this.feature = feature;
        msgDelay = (int) feature.getConfigHandler().getSetting("join_message_delay");
        startDelay = (int) feature.getConfigHandler().getSetting("rewards_start_delay");
        interval = (int) feature.getConfigHandler().getSetting("reward_interval");
        whitelist = CastUtils.safeCastToList(feature.getConfigHandler().getSetting("vote_whitelist"), String.class);
        commands = CastUtils.safeCastToList(feature.getConfigHandler().getSetting("rewards"), String.class);
    }

    /**
     * Entry point from either listener.
     */
    public void handleVote(IncomingVote vote) {
        String service = vote.serviceName();
        String username = vote.username().toLowerCase();

        if (!whitelist.contains(service)) {
            feature.getLogger().warning("Rejected vote from unwhitelisted service: " + service);
            return;
        } else {
            feature.getLogger().info("Received valid vote from " + service + " for player " + username);
        }

        Player player = Bukkit.getPlayerExact(username);

        if (player != null && player.isOnline()) {
            broadcastVote(player.getName());
            processVote(player);
        } else {
            feature.getLogger().info("Player " + username + " is offline: saving vote in cache");
            broadcastVote(username);
            queueOfflineVote(username, service);
        }
    }

    private void broadcastVote(String name) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("votereward.vote_broadcast")
                            .withPlaceholders(Map.of("player", name))
                            .forAudience(p)
                            .build()
            );
        }
    }

    private void processVote(Player player) {
        player.sendMessage(
                feature.getLocalizationHandler()
                        .getMessage("votereward.vote_received")
                        .withPlaceholders(Map.of("player", player.getName()))
                        .forAudience(player)
                        .build()
        );

        for (String template : commands) {
            String cmd = template.replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    /**
     * Store the vote in a per‐player JSON cache with 24 h TTL.
     */
    private void queueOfflineVote(String username, String service) {
        CacheDirectory dir = feature.getPlayerCacheDir();
        FileCacheStore cache = (FileCacheStore) dir.getStore(username, CacheType.JSON);

        long ttl = ((Number) feature.getConfigHandler().getSetting("cache_ttl_millis")).longValue();
        CacheValue cv = CacheValue.builder(ttl).with("service", service).build();

        String key = "vote_" + System.currentTimeMillis();
        cache.put(key, cv);
        feature.getLogger().info("Queued offline vote for " + username + " from " + service);
    }

    public void processOfflineVotesOnJoin(Player player) {
        CacheDirectory dir = feature.getPlayerCacheDir();
        FileCacheStore cache = (FileCacheStore) dir.getStore(player.getName().toLowerCase(), CacheType.JSON);

        Map<String, CacheValue> allEntries = cache.listAll();
        List<String> keys = new ArrayList<>(allEntries.keySet());
        if (keys.isEmpty()) return;

        int count = keys.size();

        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() ->
                player.sendMessage(
                        feature.getLocalizationHandler()
                                .getMessage("votereward.offline_votes_retrieved")
                                .withPlaceholders(Map.of("count", String.valueOf(count)))
                                .forAudience(player)
                                .build()
                ), BukkitTime.ticks(msgDelay)
        );

        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
            for (int i = 0; i < keys.size(); i++) {
                final String key = keys.get(i);
                final int delay = i * interval;
                feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
                    CacheValue cv = cache.get(key);
                    if (cv != null && !cv.isExpired()) {
                        processVote(player);
                        cache.remove(key);
                    }
                }, BukkitTime.ticks(delay));
            }
        }, BukkitTime.ticks(msgDelay + startDelay));
    }
}
