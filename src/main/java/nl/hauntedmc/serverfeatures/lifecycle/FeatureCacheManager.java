package nl.hauntedmc.serverfeatures.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.internal.cache.FeatureCache;

import java.io.File;

public class FeatureCacheManager {
    private static boolean initialized = false;
    private final File baseCacheFolder;

    public FeatureCacheManager(ServerFeatures plugin) {
        baseCacheFolder = new File(plugin.getDataFolder(), "cache");
        if (!initialized) {
            if (!baseCacheFolder.exists() && baseCacheFolder.mkdirs()) {
                plugin.getLogger().info("Created cache folder at " + baseCacheFolder);
            } else if (baseCacheFolder.exists()) {
                plugin.getLogger().info("Cache folder ready at " + baseCacheFolder);
            } else {
                plugin.getLogger().severe("Could not create cache folder at " + baseCacheFolder);
            }
            initialized = true;
        }
    }

    public FeatureCache createCache(String featureName, String cacheId) {
        return new FeatureCache(featureName, cacheId, baseCacheFolder);
    }

    /**
     * Scans *all* cache subfolders & files, cleans up expired entries and files.
     */
    public void cleanupAll() {
        for (File featureDir : baseCacheFolder.listFiles(File::isDirectory)) {
            FeatureCache cache = new FeatureCache(featureDir.getName(), null, baseCacheFolder);
            cache.cleanupExpiredFilesAndEntries();
        }
    }
}
