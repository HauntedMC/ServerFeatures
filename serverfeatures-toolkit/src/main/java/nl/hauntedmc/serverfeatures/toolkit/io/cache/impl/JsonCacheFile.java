package nl.hauntedmc.serverfeatures.toolkit.io.cache.impl;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import nl.hauntedmc.serverfeatures.toolkit.io.cache.CacheValue;
import nl.hauntedmc.serverfeatures.toolkit.io.cache.FileCacheStore;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Atomic JSON-backed cache store. */
public class JsonCacheFile implements FileCacheStore {
    private static final String EXPIRATION = "expirationTimestamp";
    private static final String VALUE = "value";
    private static final Type RAW_MAP_TYPE = new TypeToken<Map<String, Map<String, Object>>>() { }.getType();

    private final File file;
    private final Gson gson = new Gson();
    private final Object lock = new Object();
    private Map<String, Map<String, Object>> rawMap;

    public JsonCacheFile(File file) {
        this.file = Objects.requireNonNull(file, "file");
        ensureFileExists();
        load();
    }

    @Override public File getUnderlyingFile() { return file; }

    private void ensureFileExists() {
        if (file.exists()) return;
        try {
            File parent = file.getParentFile();
            if (parent != null) Files.createDirectories(parent.toPath());
            Files.createFile(file.toPath());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create cache file " + file, exception);
        }
    }

    private void load() {
        synchronized (lock) {
            try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                rawMap = gson.fromJson(reader, RAW_MAP_TYPE);
                if (rawMap == null) rawMap = new LinkedHashMap<>();
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot load cache file " + file, exception);
            }
        }
    }

    private void saveLocked() {
        Path target = file.toPath().toAbsolutePath();
        Path parent = target.getParent();
        try {
            if (parent != null) Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, file.getName(), ".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                    gson.toJson(rawMap, writer);
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot save cache file " + file, exception);
        }
    }

    @Override
    public void put(String key, CacheValue value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        synchronized (lock) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put(VALUE, value.getData());
            entry.put(EXPIRATION, value.getExpirationTimestamp());
            rawMap.put(key, entry);
            saveLocked();
        }
    }

    @Override public void cleanupExpired() { synchronized (lock) { cleanupExpiredLocked(); } }

    private void cleanupExpiredLocked() {
        long now = System.currentTimeMillis();
        rawMap.entrySet().removeIf(entry -> {
            Map<String, Object> value = entry.getValue();
            if (value == null) return true;
            long expiration = asLong(value.getOrDefault(EXPIRATION, -1L), -1L);
            return expiration >= 0 && now > expiration;
        });
        if (rawMap.isEmpty()) {
            if (!file.delete() && file.exists()) file.deleteOnExit();
        } else {
            saveLocked();
        }
    }

    @Override public CacheValue get(String key) { synchronized (lock) { cleanupExpiredLocked(); return toValue(rawMap.get(key)); } }

    @Override
    public Map<String, CacheValue> listAll() {
        synchronized (lock) {
            cleanupExpiredLocked();
            Map<String, CacheValue> output = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> { CacheValue cached = toValue(value); if (cached != null) output.put(key, cached); });
            return output;
        }
    }

    @Override
    public Map<String, CacheValue> find(String regex) {
        synchronized (lock) {
            cleanupExpiredLocked();
            Pattern pattern = Pattern.compile(regex);
            Map<String, CacheValue> output = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> {
                if (pattern.matcher(key).matches()) {
                    CacheValue cached = toValue(value);
                    if (cached != null) output.put(key, cached);
                }
            });
            return output;
        }
    }

    @Override public boolean isEmpty() { synchronized (lock) { return rawMap.isEmpty(); } }

    @Override
    public void delete() {
        synchronized (lock) {
            rawMap.clear();
            if (!file.delete() && file.exists()) file.deleteOnExit();
        }
    }

    private CacheValue toValue(Map<String, Object> entry) {
        if (entry == null) return null;
        return CacheValue.of(asDataMap(entry.get(VALUE)), asLong(entry.getOrDefault(EXPIRATION, -1L), -1L));
    }

    private static Map<String, Object> asDataMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> output = new LinkedHashMap<>();
        map.forEach((key, value) -> { if (key != null) output.put(String.valueOf(key), value); });
        return output;
    }

    private static long asLong(Object raw, long def) {
        if (raw instanceof Number number) return number.longValue();
        if (raw instanceof String string) {
            try { return Long.parseLong(string.trim()); } catch (NumberFormatException ignored) { return def; }
        }
        return def;
    }
}
