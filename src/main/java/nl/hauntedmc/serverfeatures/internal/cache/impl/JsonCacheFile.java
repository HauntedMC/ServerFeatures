// nl/hauntedmc/serverfeatures/internal/cache/impl/JsonCacheFile.java
package nl.hauntedmc.serverfeatures.internal.cache.impl;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import nl.hauntedmc.serverfeatures.internal.cache.CacheValue;
import nl.hauntedmc.serverfeatures.internal.cache.FileCacheStore;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Pattern;

/**
 * JSON-backed cache store, identical per-entry TTL semantics as YAML.
 */
public class JsonCacheFile implements FileCacheStore {
    private static final String EXP_TS = "expirationTimestamp";
    private static final String VALUE  = "value";

    private final File file;
    private final Gson gson = new Gson();
    /** key → list of raw entry maps */
    private Map<String, List<Map<String, Object>>> rawMap;

    private static final Type RAW_MAP_TYPE =
            new TypeToken<Map<String, List<Map<String, Object>>>>() {}.getType();

    public JsonCacheFile(File file) {
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
            } catch (IOException e) {
                throw new IllegalStateException("Cannot create JSON cache " + file, e);
            }
        }
    }

    private void load() {
        try (Reader r = new FileReader(file)) {
            rawMap = gson.fromJson(r, RAW_MAP_TYPE);
            if (rawMap == null) rawMap = new LinkedHashMap<>();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load JSON cache " + file, e);
        }
    }

    private void save() {
        try (Writer w = new FileWriter(file)) {
            gson.toJson(rawMap, w);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot save JSON cache " + file, e);
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
        rawMap.put(key, List.of(entry));
        save();
    }

    @Override
    public CacheValue getEntry(String key) {
        List<CacheValue> list = getEntries(key);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<CacheValue> getEntries(String key) {
        List<Map<String, Object>> rawList = rawMap.getOrDefault(key, Collections.emptyList());
        List<Map<String, Object>> kept = new ArrayList<>();
        List<CacheValue> out = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (Map<String, Object> raw : rawList) {
            long ts = ((Number) raw.getOrDefault(EXP_TS, -1)).longValue();
            Map<String, Object> data = (Map<String, Object>) raw.get(VALUE);
            if (ts < 0 || now <= ts) {
                kept.add(raw);
                out.add(new CacheValue(data, ts));
            }
        }

        if (kept.size() != rawList.size()) {
            if (kept.isEmpty()) rawMap.remove(key);
            else rawMap.put(key, kept);
            save();
        }
        return out;
    }

    @Override
    public Map<String, List<CacheValue>> getAllEntries() {
        Map<String, List<CacheValue>> result = new LinkedHashMap<>();
        for (String key : rawMap.keySet()) {
            List<CacheValue> list = getEntries(key);
            if (!list.isEmpty()) result.put(key, list);
        }
        return result;
    }

    @Override
    public Map<String, List<CacheValue>> getMatchingEntries(String regex) {
        Pattern p = Pattern.compile(regex);
        Map<String, List<CacheValue>> result = new LinkedHashMap<>();
        for (String key : rawMap.keySet()) {
            if (!p.matcher(key).matches()) continue;
            List<CacheValue> list = getEntries(key);
            if (!list.isEmpty()) result.put(key, list);
        }
        return result;
    }

    @Override
    public void cleanupExpired() {
        for (String key : new ArrayList<>(rawMap.keySet())) {
            getEntries(key); // side‐effect purges & saves
        }
        save();
    }

    @Override
    public boolean isEmpty() {
        return rawMap.isEmpty();
    }

    @Override
    public void delete() {
        if (!file.delete()) file.deleteOnExit();
    }
}
