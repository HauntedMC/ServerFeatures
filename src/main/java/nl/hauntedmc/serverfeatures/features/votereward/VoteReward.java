package nl.hauntedmc.serverfeatures.features.votereward;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.votereward.listener.TestListener;
import nl.hauntedmc.serverfeatures.internal.cache.CacheDirectory;
import nl.hauntedmc.serverfeatures.lifecycle.FeatureCacheManager;
import nl.hauntedmc.serverfeatures.features.votereward.meta.Meta;

public class VoteReward extends BukkitBaseFeature<Meta> {

    /** Shared per-player cache directory (plugins/.../cache/voteReward-players/). */
    private CacheDirectory playerCacheDir;

    public VoteReward(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        FeatureCacheManager cacheMgr = getLifecycleManager().getCacheManager();
        // Create once for all players
        this.playerCacheDir = cacheMgr.getCacheDirectory(getFeatureName(), "players");

        // Register listener (it’ll pull the directory from the feature)
        getLifecycleManager()
                .getListenerManager()
                .registerListener(new TestListener(this));
    }

    @Override
    public void disable() {
        // nothing special
    }

    /** Expose the per-player cache directory. */
    public CacheDirectory getPlayerCacheDir() {
        return playerCacheDir;
    }
}
