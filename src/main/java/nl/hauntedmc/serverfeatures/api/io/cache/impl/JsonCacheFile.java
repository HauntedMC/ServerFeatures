package nl.hauntedmc.serverfeatures.api.io.cache.impl;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import nl.hauntedmc.serverfeatures.api.io.cache.CacheValue;
import nl.hauntedmc.serverfeatures.api.io.cache.FileCacheStore;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Pattern;

public class JsonCacheFile implements FileCacheStore {
    private static final String EXP_TS = "expirationTimestamp";
    private static final String VALUE = "value";

    private final File file;
    private final Gson gson = new Gson();
    // key → single { "value": Map, "expirationTimestamp": long }
    private Map<String, Map<String, Object>> rawMap;
    private static final Type RAW_MAP_TYPE =
            new TypeToken<Map<String, Map<String, Object>>>() {
            }.getType();

    public JsonCacheFile(File file) {
        this.file = Objects.requireNonNull(file, "file");
        ensureFileExists();
        load();
    }

    @Override
    public File getUnderlyingFile() {
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
        try (Reader r = new FileReader(file)) {
            rawMap = gson.fromJson(r, RAW_MAP_TYPE);
            if (rawMap == null) rawMap = new LinkedHashMap<>();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load cache file " + file, ex);
        }
    }

    private void save() {
        try (Writer w = new FileWriter(file)) {
            gson.toJson(rawMap, w);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot save cache file " + file, ex);
        }
    }

    @Override
    public void put(String key, CacheValue value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put(VALUE, value.getData());
        entry.put(EXP_TS, value.getExpirationTimestamp());
        rawMap.put(key, entry);
        save();
    }

    @Override
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        rawMap.entrySet().removeIf(e -> {
            Map<String, Object> ent = e.getValue();
            long ts = ((Number) ent.getOrDefault(EXP_TS, -1L)).longValue();
            return ts >= 0 && now > ts;
        });
        if (rawMap.isEmpty()) {
            delete();
        } else {
            save();
        }
    }

    @Override
    public CacheValue get(String key) {
        cleanupExpired();
        Map<String, Object> entry = rawMap.get(key);
        if (entry == null) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) entry.get(VALUE);
        long ts = ((Number) entry.getOrDefault(EXP_TS, -1L)).longValue();
        return CacheValue.of(data, ts);
    }

    @Override
    public Map<String, CacheValue> listAll() {
        cleanupExpired();
        Map<String, CacheValue> result = new LinkedHashMap<>();
        for (String key : rawMap.keySet()) {
            CacheValue cv = get(key);
            if (cv != null) result.put(key, cv);
        }
        return result;
    }

    @Override
    public void remove(String key) {
        Objects.requireNonNull(key, "key");
        // remove the entry
        if (rawMap.remove(key) != null) {
            // if nothing left, delete the file; otherwise save the change
            if (rawMap.isEmpty()) {
                delete();
            } else {
                save();
            }
        }
    }

    public Set<String> getKeys() {
        return new LinkedHashSet<>(rawMap.keySet());
    }

    @Override
    public Map<String, CacheValue> find(String regex) {
        cleanupExpired();
        Pattern pat = Pattern.compile(regex);
        Map<String, CacheValue> result = new LinkedHashMap<>();
        for (String key : rawMap.keySet()) {
            if (pat.matcher(key).matches()) {
                CacheValue cv = get(key);
                if (cv != null) result.put(key, cv);
            }
        }
        return result;
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
