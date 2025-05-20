package nl.hauntedmc.serverfeatures.features.votereward.listener;

import nl.hauntedmc.serverfeatures.features.votereward.VoteReward;
import nl.hauntedmc.serverfeatures.internal.cache.CacheValue;
import nl.hauntedmc.serverfeatures.internal.cache.FileCacheStore;
import nl.hauntedmc.serverfeatures.internal.cache.CacheType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;

/**
 * Example listener using the centrally-created per-player cache directory.
 */
public class TestListener implements Listener {
    private static final long TTL_MILLIS = 24L * 60 * 60 * 1000; // 24h

    private final VoteReward feature;

    public TestListener(VoteReward feature) {
        this.feature        = feature;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // inside cache/voteReward-players/
        FileCacheStore cache = (FileCacheStore) feature.getPlayerCacheDir().getStore(player.getName(), CacheType.YAML);

        Map<String, Object> data = Map.of("loginTs", System.currentTimeMillis());
        cache.setEntry("lastLogin", data, TTL_MILLIS);

        feature.getPlugin().getLogger()
                .info("Recorded lastLogin for " + player.getName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        FileCacheStore cache = (FileCacheStore) feature.getPlayerCacheDir().getStore(player.getName(), CacheType.YAML);

        CacheValue cv = cache.getEntry("lastLogin");
        if (cv != null) {
            Object tsObj = cv.getData().get("loginTs");
            if (tsObj instanceof Number loginTs) {
                long session = System.currentTimeMillis() - loginTs.longValue();
                feature.getPlugin().getLogger()
                        .info(player.getName() + " session length: " + session + " ms");
            } else {
                feature.getPlugin().getLogger()
                        .warning(player.getName() + " had malformed lastLogin data.");
            }
        } else {
            feature.getPlugin().getLogger()
                    .info(player.getName() + " had no valid lastLogin (expired or missing).");
        }

        cache.cleanupExpired();
        if (cache.getAllEntries().isEmpty()) {
            cache.delete();
            feature.getPlugin().getLogger()
                    .info("Deleted empty cache file for " + player.getName());
        }
    }
}
