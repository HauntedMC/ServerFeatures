package nl.hauntedmc.serverfeatures.features.votereward;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.votereward.meta.Meta;
import nl.hauntedmc.serverfeatures.internal.cache.CacheFile;
import nl.hauntedmc.serverfeatures.internal.cache.FeatureCache;

public class VoteReward extends BukkitBaseFeature<Meta> {


    public VoteReward(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    /**
     * Returns default config values for the Titles feature.
     */
    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        return messages;
    }

    @Override
    public void initialize() {
        FeatureCache cache = getLifecycleManager()
                .getCacheManager()
                .createCache(getFeatureName(), "queue");
        CacheFile playerFile = cache.getFile("test");
        playerFile.cleanupExpiredEntries();
        playerFile.setEntry(
                "vote-" + System.currentTimeMillis(),
                "MIEP",
                60 * 1000L
        );
    }

    @Override
    public void disable() {
    }
}
