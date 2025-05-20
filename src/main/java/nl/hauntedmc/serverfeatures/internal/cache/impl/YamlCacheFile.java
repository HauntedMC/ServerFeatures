// nl/hauntedmc/serverfeatures/internal/cache/impl/YamlCacheFile.java
package nl.hauntedmc.serverfeatures.internal.cache.impl;

import nl.hauntedmc.serverfeatures.internal.cache.CacheValue;
import nl.hauntedmc.serverfeatures.internal.cache.FileCacheStore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * YAML-backed implementation: each key → list of { value:Map, expirationTimestamp:long }.
 */
public class YamlCacheFile implements FileCacheStore {
    private static final String EXP_TS = "expirationTimestamp";
    private static final String VALUE  = "value";

    private final File file;
    private FileConfiguration config;

    public YamlCacheFile(File file) {
        this.file = Objects.requireNonNull(file, "file");
        ensureFileExists();
        load();
    }

    @Override public File getUnderlyingFile() {
        return file;
    }

    private void ensureFileExists() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException ex) {
                throw new IllegalStateException("Cannot create cache file " + file, ex);
            }
        }
    }

    private void load() {
        config = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot save cache file " + file, ex);
        }
    }

    @Override
    public void setEntry(String key, Map<String, Object> data, long ttlMillis) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(data, "data");
        long expiresAt = System.currentTimeMillis() + ttlMillis;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put(VALUE, data);
        entry.put(EXP_TS, expiresAt);
        config.set(key, List.of(entry));
        save();
    }

    @Override
    public CacheValue getEntry(String key) {
        List<CacheValue> list = getEntries(key);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<CacheValue> getEntries(String key) {
        List<Map<String, Object>> raws = purgeAndGetRawEntries(key);
        List<CacheValue> out = new ArrayList<>(raws.size());
        for (Map<String, Object> raw : raws) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) raw.get(VALUE);
            long ts = ((Number) raw.getOrDefault(EXP_TS, -1)).longValue();
            out.add(new CacheValue(data, ts));
        }
        return out;
    }

    @Override
    public Map<String, List<CacheValue>> getAllEntries() {
        Map<String, List<CacheValue>> result = new HashMap<>();
        for (String key : config.getKeys(false)) {
            List<CacheValue> entries = getEntries(key);
            if (!entries.isEmpty()) result.put(key, entries);
        }
        return result;
    }

    @Override
    public Map<String, List<CacheValue>> getMatchingEntries(String regex) {
        Pattern pat = Pattern.compile(regex);
        Map<String, List<CacheValue>> result = new HashMap<>();
        for (String key : config.getKeys(false)) {
            if (!pat.matcher(key).matches()) continue;
            List<CacheValue> entries = getEntries(key);
            if (!entries.isEmpty()) result.put(key, entries);
        }
        return result;
    }

    @Override
    public void cleanupExpired() {
        // prune by key
        for (String key : new HashSet<>(config.getKeys(false))) {
            purgeAndGetRawEntries(key);
        }
        // remove any now-empty keys
        for (String key : new HashSet<>(config.getKeys(false))) {
            if (config.getMapList(key).isEmpty()) {
                config.set(key, null);
            }
        }
        save();
    }

    @Override
    public boolean isEmpty() {
        return config.getKeys(false).isEmpty();
    }

    @Override
    public void delete() {
        if (!file.delete()) file.deleteOnExit();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> purgeAndGetRawEntries(String key) {
        List<Map<?, ?>> rawList = config.getMapList(key);
        if (rawList.isEmpty()) return Collections.emptyList();

        // cast to our expected type
        List<Map<String, Object>> original = new ArrayList<>(rawList.size());
        for (Map<?, ?> e : rawList) original.add((Map<String, Object>) e);

        long now = System.currentTimeMillis();
        List<Map<String, Object>> kept = new ArrayList<>();
        for (Map<String, Object> entry : original) {
            long ts = ((Number) entry.getOrDefault(EXP_TS, -1)).longValue();
            if (ts < 0 || now <= ts) kept.add(entry);
        }

        if (kept.size() != original.size()) {
            config.set(key, kept.isEmpty() ? null : kept);
            save();
        }
        return kept;
    }
}
