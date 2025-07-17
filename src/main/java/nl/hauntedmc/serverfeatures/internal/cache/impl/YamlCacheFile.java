package nl.hauntedmc.serverfeatures.internal.cache.impl;

import nl.hauntedmc.serverfeatures.internal.cache.CacheValue;
import nl.hauntedmc.serverfeatures.internal.cache.FileCacheStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

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

    @Override public File getUnderlyingFile() { return file; }

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
    public void put(String key, CacheValue value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Map<String,Object> entry = new LinkedHashMap<>();
        entry.put(VALUE, value.getData());
        entry.put(EXP_TS, value.getExpirationTimestamp());
        config.set(key, entry);
        save();
    }

    @Override
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        for (String key : new HashSet<>(config.getKeys(false))) {
            long ts = config.getLong(key + "." + EXP_TS, -1L);
            if (ts >= 0 && now > ts) {
                config.set(key, null);
            }
        }
        if (config.getKeys(false).isEmpty()) {
            delete();
        } else {
            save();
        }
    }

    @Override
    public void remove(String key) {
        Objects.requireNonNull(key, "key");
        // remove the entry
        config.set(key, null);
        // if nothing left, delete the file; otherwise save the change
        if (config.getKeys(false).isEmpty()) {
            delete();
        } else {
            save();
        }
    }

    public Set<String> getKeys() {
        // return a copy to avoid concurrent-mod issues
        return new HashSet<>(config.getKeys(false));
    }

    @Override
    public CacheValue get(String key) {
        cleanupExpired();
        // if the whole key is gone (or expired) we’re done
        if (!config.contains(key)) return null;

        // get the section for this key
        ConfigurationSection sec = config.getConfigurationSection(key);
        if (sec == null) return null;

        // get the 'value' child as its own section
        ConfigurationSection valueSection = sec.getConfigurationSection(VALUE);
        if (valueSection == null) return null;

        // now extract a plain Map<String,Object> from it
        Map<String, Object> data = valueSection.getValues(false);

        // pull the timestamp directly from the parent section
        long ts = sec.getLong(EXP_TS, -1L);
        return CacheValue.of(data, ts);
    }

    @Override
    public Map<String, CacheValue> listAll() {
        cleanupExpired();
        Map<String, CacheValue> result = new LinkedHashMap<>();
        for (String key : config.getKeys(false)) {
            CacheValue cv = get(key);
            if (cv != null) result.put(key, cv);
        }
        return result;
    }

    @Override
    public Map<String, CacheValue> find(String regex) {
        cleanupExpired();
        Pattern pat = Pattern.compile(regex);
        Map<String, CacheValue> result = new LinkedHashMap<>();
        for (String key : config.getKeys(false)) {
            if (pat.matcher(key).matches()) {
                CacheValue cv = get(key);
                if (cv != null) result.put(key, cv);
            }
        }
        return result;
    }

    @Override
    public boolean isEmpty() {
        return config.getKeys(false).isEmpty();
    }

    @Override
    public void delete() {
        if (!file.delete()) file.deleteOnExit();
    }
}
