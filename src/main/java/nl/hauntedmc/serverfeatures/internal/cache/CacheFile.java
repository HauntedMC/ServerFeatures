package nl.hauntedmc.serverfeatures.internal.cache;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Lightweight YAML‑backed cache file with optional per‑file and per‑entry TTL support.
 * <p>
 *   Public API keeps a clear separation of concerns:
 *   <ul>
 *     <li><strong>Mutation</strong>: {@link #setEntry(String, Object, long)}</li>
 *     <li><strong>Query</strong>: {@link #getEntry(String)}, {@link #getAllValues(String)}, {@link #getAllValues()},
 *         {@link #findKeys(String)}</li>
 *     <li><strong>Maintenance</strong>: {@link #cleanupExpiredEntries()}, {@link #isExpired()}, {@link #isEmpty()}</li>
 *   </ul>
 */
public class CacheFile {

    /* ───────────────────────────────────────────────────────────── Constants ───────────────────────────────────────────── */
    private static final String META_NODE   = "__meta__";
    private static final String EXP_TS_NODE = "expirationTimestamp";
    private static final String VALUE_NODE  = "value";

    /* ────────────────────────────────────────────────────────────── Fields ─────────────────────────────────────────────── */
    private final File file;
    private FileConfiguration config;

    /* ───────────────────────────────────────────────────────────── Constructors ─────────────────────────────────────────── */
    public CacheFile(File file) {
        this.file = Objects.requireNonNull(file, "file");
        ensureFileExists();
        load();
    }

    /* ─────────────────────────────────────────────────────── File + YAML helpers ───────────────────────────────────────── */

    private void ensureFileExists() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to create cache file " + file, ex);
            }
        }
    }

    private void load() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to save cache file " + file, ex);
        }
    }

    /* ───────────────────────────────────────────────────────── File‑level TTL ───────────────────────────────────────────── */

    /** Set an absolute TTL for the entire cache file. */
    public void setExpiration(long ttlMillis) {
        long expiresAt = System.currentTimeMillis() + ttlMillis;
        config.set(META_NODE + "." + EXP_TS_NODE, expiresAt);
        save();
    }

    /** @return {@code true} if the file exceeded its TTL and was deleted. */
    public boolean isExpired() {
        long ts = config.getLong(META_NODE + "." + EXP_TS_NODE, -1);
        boolean expired = ts > 0 && System.currentTimeMillis() > ts;
        if (expired) delete();
        return expired;
    }

    public void delete() {
        if (!file.delete()) file.deleteOnExit();
    }

    /* ────────────────────────────────────────────────────── Mutation public API ────────────────────────────────────────── */

    /**
     * Overwrite the specified key with a <i>single</i> value and TTL. Existing data is discarded.
     */
    public void setEntry(String key, Object value, long ttlMillis) {
        Objects.requireNonNull(key, "key");
        long expiresAt = System.currentTimeMillis() + ttlMillis;
        Map<String, Object> entry = Map.of(VALUE_NODE, value, EXP_TS_NODE, expiresAt);
        config.set(key, List.of(entry));
        save();
    }

    /* ─────────────────────────────────────────────────────── Query public API ──────────────────────────────────────────── */

    /** Return the first live value stored under {@code key}, or {@code null}. */
    public Object getEntry(String key) {
        List<Object> list = getAllValues(key);
        return list.isEmpty() ? null : list.getFirst();
    }

    /** Return a map of every key → list of live values. */
    public Map<String, List<Object>> getAllValues() {
        Map<String, List<Object>> result = new HashMap<>();
        for (String key : config.getKeys(false)) {
            if (META_NODE.equals(key)) continue;
            List<Object> live = getAllValues(key); // prunes automatically
            if (!live.isEmpty()) result.put(key, live);
        }
        return result;
    }

    /** Return live values for every key matching {@code regex}. */
    public Map<String, List<Object>> findKeys(String regex) {
        Pattern pattern = Pattern.compile(regex);
        Map<String, List<Object>> matches = new HashMap<>();
        for (String key : config.getKeys(false)) {
            if (META_NODE.equals(key) || !pattern.matcher(key).matches()) continue;
            List<Object> live = getAllValues(key);
            if (!live.isEmpty()) matches.put(key, live);
        }
        return matches;
    }


    /* ───────────────────────────────────────────────────── Maintenance public API ──────────────────────────────────────── */
    /** Remove expired values from all keys (and delete empty keys). */
    public void cleanupExpiredEntries() {
        for (String key : new HashSet<>(config.getKeys(false))) { // defensive copy
            if (META_NODE.equals(key)) continue;
            purgeAndGetRawEntries(key);
        }
    }

    /** @return {@code true} if no user keys remain (i.e. only __meta__). */
    public boolean isEmpty() {
        return config.getKeys(false).stream().allMatch(META_NODE::equals);
    }


    /* ───────────────────────────────────────────────────── Internal utility ─────────────────────────────────────────────── */

    /**
     * Purge expired entry maps under {@code key}, persist if modified, and return the kept raw list.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> purgeAndGetRawEntries(String key) {
        List<Map<?, ?>> raw = config.getMapList(key);
        if (raw.isEmpty()) return Collections.emptyList();

        // Cast each map to <String,Object> – Bukkit config stores raw maps.
        List<Map<String, Object>> original = new ArrayList<>(raw.size());
        for (Map<?, ?> entry : raw) {
            original.add((Map<String, Object>) entry);
        }

        long now = System.currentTimeMillis();
        List<Map<String, Object>> kept = new ArrayList<>();
        for (Map<String, Object> entry : original) {
            long ts = ((Number) entry.getOrDefault(EXP_TS_NODE, -1)).longValue();
            if (ts < 0 || now <= ts) kept.add(entry);
        }

        if (kept.size() != original.size()) {
            config.set(key, kept.isEmpty() ? null : kept);
            save();
        }
        return kept;
    }

    private static List<Object> extractValues(List<Map<String, Object>> raws) {
        if (raws.isEmpty()) return Collections.emptyList();
        List<Object> out = new ArrayList<>(raws.size());
        for (Map<String, Object> entry : raws) out.add(entry.get(VALUE_NODE));
        return out;
    }

    /** Return <b>all</b> live values stored under {@code key}. */
    private List<Object> getAllValues(String key) {
        return extractValues(purgeAndGetRawEntries(key));
    }
}
