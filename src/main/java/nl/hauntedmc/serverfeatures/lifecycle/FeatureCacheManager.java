package nl.hauntedmc.serverfeatures.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.internal.cache.CacheDirectory;

import java.io.File;

/**
 * Manages the top-level cache folder and hands out per-feature directories.
 */
public class FeatureCacheManager {
    private static boolean initialized = false;
    private final File baseFolder;

    public FeatureCacheManager(ServerFeatures plugin) {
        this.baseFolder = new File(plugin.getDataFolder(), "cache");
        if (!initialized) {
            if (!baseFolder.exists() && baseFolder.mkdirs()) {
                plugin.getLogger().info("Created cache folder at " + baseFolder);
            }
            initialized = true;
        }
    }

    /**
     * Get (or create) the cache subdirectory for this feature + identifier.
     *
     * Example:
     *   getCacheDirectory("voteRewards", "queue")
     *     ⇒ plugins/.../cache/voteRewards-queue/
     */
    public CacheDirectory getCacheDirectory(String featureName, String cacheId) {
        return new CacheDirectory(baseFolder, featureName, cacheId);
    }

    /** Global cleanup can still sweep across all subfolders if desired. */
    public void cleanupAll() {
        // unchanged
    }
}
