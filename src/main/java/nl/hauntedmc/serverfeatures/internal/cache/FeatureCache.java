package nl.hauntedmc.serverfeatures.internal.cache;

import java.io.File;
import java.util.Arrays;

public class FeatureCache {
    private final File cacheDir;

    /**
     * @param featureName "voterewards"
     * @param cacheId     "queue" (if null, featureDirName is used directly)
     * @param baseFolder  plugin/data/cache/
     */
    public FeatureCache(String featureName, String cacheId, File baseFolder) {
        String folderName = (cacheId == null)
                ? featureName
                : featureName + "-" + cacheId;
        cacheDir = new File(baseFolder, folderName);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }

    /** Get a CacheFile for "cacheDir/<fileName>.yml". */
    public CacheFile getFile(String fileName) {
        return new CacheFile(new File(cacheDir, fileName + ".yml"));
    }

    /** Walk all .yml files in this cacheDir and expire as needed. */
    public void cleanupExpiredFilesAndEntries() {
        File[] files = cacheDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        Arrays.stream(files).forEach(f -> {
            CacheFile cf = new CacheFile(f);
            cf.cleanupExpiredEntries();
            // file‐level expiration will delete it if necessary
            if (cf.isExpired() || cf.isEmpty()) {
                cf.delete();
            }
        });
        // if folder is now empty, delete it
        if (cacheDir.list().length == 0) {
            cacheDir.delete();
        }
    }
}
