package nl.hauntedmc.serverfeatures.features.votereward.listener;

import nl.hauntedmc.serverfeatures.features.votereward.VoteReward;
import nl.hauntedmc.serverfeatures.internal.cache.CacheDirectory;
import nl.hauntedmc.serverfeatures.internal.cache.CacheValue;
import nl.hauntedmc.serverfeatures.internal.cache.FileCacheStore;
import nl.hauntedmc.serverfeatures.internal.cache.CacheType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Updated to use only CacheValue (no raw maps).
 */
public class TestListener implements Listener {
    private static final long TTL_MILLIS = 10 * 1000;

    private final VoteReward feature;
    private final CacheDirectory playerDir;

    public TestListener(VoteReward feature) {
        this.feature    = feature;
        this.playerDir  = feature.getPlayerCacheDir();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        FileCacheStore cache = (FileCacheStore) playerDir.getStore(p.getName(), CacheType.JSON);

        // Build a CacheValue with a single field “loginTs”
        CacheValue cv = CacheValue.builder(TTL_MILLIS)
                .with("loginTs", System.currentTimeMillis())
                .build();
        cache.put("lastLogin", cv);

        feature.getPlugin().getLogger()
                .info("Recorded lastLogin for " + p.getName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        FileCacheStore cache = (FileCacheStore) playerDir.getStore(p.getName(), CacheType.JSON);

        CacheValue cv = cache.get("lastLogin");
        if (cv != null) {
            Object tsObj = cv.getData().get("loginTs");
            if (tsObj instanceof Number loginTs) {
                long session = System.currentTimeMillis() - loginTs.longValue();
                feature.getPlugin().getLogger()
                        .info(p.getName() + " session length: " + session + " ms");
            } else {
                feature.getPlugin().getLogger()
                        .warning(p.getName() + " had malformed lastLogin data.");
            }
        } else {
            feature.getPlugin().getLogger()
                    .info(p.getName() + " had no valid lastLogin (expired or missing).");
        }

    }
}