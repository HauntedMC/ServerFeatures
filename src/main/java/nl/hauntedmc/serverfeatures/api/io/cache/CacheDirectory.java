// nl/hauntedmc/serverfeatures/internal/cache/FeatureCacheDirectory.java
package nl.hauntedmc.serverfeatures.api.io.cache;

import nl.hauntedmc.serverfeatures.api.io.cache.impl.JsonCacheFile;
import nl.hauntedmc.serverfeatures.api.io.cache.impl.SqliteCacheFile;
import nl.hauntedmc.serverfeatures.api.io.cache.impl.YamlCacheFile;

import java.io.File;

/**
 * Represents a feature-specific subfolder under plugins/.../cache/.
 * Use {@link #getStore} to make per-file stores inside it.
 */
public class CacheDirectory {
    private final File dir;

    /**
     * @param baseCacheFolder plugins/.../cache/
     * @param featureName     e.g. "voteRewards"
     * @param cacheId         e.g. "queue" or player-name
     */
    public CacheDirectory(File baseCacheFolder, String featureName, String cacheId) {
        this.dir = new File(baseCacheFolder, featureName + "-" + cacheId);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create cache directory: " + dir);
        }
    }

    /** The underlying directory on disk. */
    public File getDirectory() {
        return dir;
    }

    /**
     * Create or open a cache store file inside this directory.
     *
     * @param fileName name without extension — e.g. a player name or "logs"
     * @param type     YAML, JSON, or SQLITE
     */
    public CacheStore getStore(String fileName, CacheType type) {
        File file;
        return switch (type) {
            case YAML -> {
                file = new File(dir, fileName + ".yml");
                yield new YamlCacheFile(file);
            }
            case JSON -> {
                file = new File(dir, fileName + ".json");
                yield new JsonCacheFile(file);
            }
            case SQLITE -> {
                file = new File(dir, fileName + ".db");
                yield new SqliteCacheFile(file);
            }
        };
    }
}
