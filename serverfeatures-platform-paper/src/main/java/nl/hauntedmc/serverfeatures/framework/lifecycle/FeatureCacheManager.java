package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.cache.CacheDirectory;

import java.io.File;
import java.util.Objects;

/**
 * Manages the top-level cache folder and hands out per-feature directories.
 */
public class FeatureCacheManager {
    private final File baseFolder;

    public FeatureCacheManager(ServerFeatures plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.baseFolder = new File(plugin.getDataFolder(), "cache");
        if (baseFolder.exists() && !baseFolder.isDirectory()) {
            throw new IllegalStateException("Cache path is not a directory: " + baseFolder);
        }
        if (!baseFolder.exists()) {
            if (!baseFolder.mkdirs()) {
                throw new IllegalStateException("Could not create cache folder: " + baseFolder);
            }
            plugin.getLogger().info("Created cache folder at " + baseFolder);
        }
    }

    /**
     * Get (or create) the cache subdirectory for this feature + identifier.
     * Example:
     * getCacheDirectory("voteRewards", "queue")
     * ⇒ plugins/.../cache/voteRewards-queue/
     */
    public CacheDirectory getCacheDirectory(String featureName, String cacheId) {
        return new CacheDirectory(baseFolder, featureName, cacheId);
    }

    /**
     * Global cleanup can still sweep across all subfolders if desired.
     */
    public void cleanupAll() {
        // unchanged
    }
}
