package nl.hauntedmc.serverfeatures.framework.config;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigTypes;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Feature-level config handler with concurrency-safe mutation API.
 */
public class FeatureConfigHandler extends MainConfigHandler {

    private final String featureName;

    public FeatureConfigHandler(ServerFeatures plugin, String featureName) {
        super(plugin);
        this.featureName = featureName;
    }

    public Object getSetting(String key) {
        rw.readLock().lock();
        try {
            return config.get(base(key));
        } finally {
            rw.readLock().unlock();
        }
    }

    public <T> T getSetting(String key, Class<T> type) {
        return ConfigTypes.convert(getSetting(key), type);
    }

    public <T> T getSetting(String key, Class<T> type, T defaultValue) {
        return ConfigTypes.convertOrDefault(getSetting(key), type, defaultValue);
    }

    public Map<String, Object> getMap(String key) {
        return getSetting(key, Map.class);
    }

    public Map<String, Object> getMap(String key, Map<String, Object> def) {
        return getSetting(key, Map.class, def);
    }

    public <T> List<T> getList(String key, Class<T> elemType) {
        return ConfigTypes.convertList(getSetting(key), elemType);
    }

    public <T> List<T> getList(String key, Class<T> elemType, List<T> def) {
        try {
            return ConfigTypes.convertList(getSetting(key), elemType);
        } catch (Exception ignored) {
            return def;
        }
    }

    public <V> Map<String, V> getMapValues(String key, Class<V> valueType) {
        return ConfigTypes.convertMapValues(getSetting(key), valueType);
    }

    public <V> Map<String, V> getMapValues(String key, Class<V> valueType, Map<String, V> def) {
        try {
            return ConfigTypes.convertMapValues(getSetting(key), valueType);
        } catch (Exception ignored) {
            return def;
        }
    }

    public ConfigNode node() {
        rw.readLock().lock();
        try {
            ConfigurationSection section = config.getConfigurationSection("features." + featureName);
            return ConfigNode.ofRaw(section, "features." + featureName);
        } finally {
            rw.readLock().unlock();
        }
    }

    public ConfigNode node(String key) {
        rw.readLock().lock();
        try {
            return ConfigNode.ofRaw(config.get(base(key)), base(key));
        } finally {
            rw.readLock().unlock();
        }
    }

    public ConfigNode nodeAt(String dottedPath) {
        return node().getAt(dottedPath);
    }

    public <T> T getAt(String dottedPath, Class<T> type) {
        return node().getAt(dottedPath).asRequired(type);
    }

    public <T> T getAt(String dottedPath, Class<T> type, T defaultValue) {
        return node().getAt(dottedPath).as(type, defaultValue);
    }

    protected String base(String key) {
        return "features." + featureName + (key == null || key.isEmpty() ? "" : "." + key);
    }

    public void put(String dottedPath, Object value) {
        rw.writeLock().lock();
        try {
            config.set(base(dottedPath), value);
            configResource.save();
        } finally {
            rw.writeLock().unlock();
        }
    }

    /** New: remove a path (feature-aware). Equivalent to set(null). */
    public void remove(String dottedPath) {
        rw.writeLock().lock();
        try {
            config.set(base(dottedPath), null);
            configResource.save();
        } finally {
            rw.writeLock().unlock();
        }
    }

    public boolean putIfAbsent(String dottedPath, Object value) {
        rw.writeLock().lock();
        try {
            String path = base(dottedPath);
            if (!config.contains(path)) {
                config.set(path, value);
                configResource.save();
                return true;
            }
            return false;
        } finally {
            rw.writeLock().unlock();
        }
    }

    public <T> T compute(String dottedPath, Class<T> type, UnaryOperator<T> updateFn, Supplier<T> init) {
        rw.writeLock().lock();
        try {
            String path = base(dottedPath);
            T cur = config.contains(path) ? ConfigTypes.convert(config.get(path), type) : null;
            if (cur == null && init != null) cur = init.get();
            T next = Objects.requireNonNull(updateFn.apply(cur), "updateFn returned null");
            config.set(path, next);
            configResource.save();
            return next;
        } finally {
            rw.writeLock().unlock();
        }
    }

    public void appendToList(String dottedPath, Object value) {
        rw.writeLock().lock();
        try {
            String path = base(dottedPath);
            List<?> current = config.getList(path);
            List<Object> list = new ArrayList<>();
            if (current != null) list.addAll(current);
            list.add(value);
            config.set(path, list);
            configResource.save();
        } finally {
            rw.writeLock().unlock();
        }
    }

    public int removeFromList(String dottedPath, Predicate<Object> predicate) {
        rw.writeLock().lock();
        try {
            String path = base(dottedPath);
            List<?> current = config.getList(path);
            if (current == null || current.isEmpty()) return 0;
            List<Object> list = new ArrayList<>(current.size());
            list.addAll(current);
            int before = list.size();
            list.removeIf(predicate);
            if (list.size() != before) {
                config.set(path, list);
                configResource.save();
            }
            return before - list.size();
        } finally {
            rw.writeLock().unlock();
        }
    }

    public void batch(Consumer<FeatureBatch> tx) {
        rw.writeLock().lock();
        try {
            FeatureBatch batch = new FeatureBatch();
            tx.accept(batch);
            if (batch.changed) {
                configResource.save();
            }
        } finally {
            rw.writeLock().unlock();
        }
    }

    public final class FeatureBatch {
        private boolean changed = false;

        private FeatureBatch() { }

        public FeatureBatch put(String dottedPath, Object value) {
            config.set(base(dottedPath), value);
            changed = true;
            return this;
        }

        public FeatureBatch putIfAbsent(String dottedPath, Object value) {
            if (!config.contains(base(dottedPath))) {
                config.set(base(dottedPath), value);
                changed = true;
            }
            return this;
        }

        public <T> FeatureBatch compute(String dottedPath, Class<T> type, UnaryOperator<T> updateFn, Supplier<T> init) {
            String path = base(dottedPath);
            T cur = config.contains(path) ? ConfigTypes.convert(config.get(path), type) : null;
            if (cur == null && init != null) cur = init.get();
            T next = Objects.requireNonNull(updateFn.apply(cur));
            config.set(path, next);
            changed = true;
            return this;
        }

        public FeatureBatch appendToList(String dottedPath, Object value) {
            String path = base(dottedPath);
            List<?> current = config.getList(path);
            List<Object> list = new ArrayList<>();
            if (current != null) list.addAll(current);
            list.add(value);
            config.set(path, list);
            changed = true;
            return this;
        }

        public FeatureBatch removeFromList(String dottedPath, Predicate<Object> predicate) {
            String path = base(dottedPath);
            List<?> current = config.getList(path);
            if (current == null || current.isEmpty()) return this;
            List<Object> list = new ArrayList<>(current);
            int before = list.size();
            list.removeIf(predicate);
            if (list.size() != before) {
                config.set(path, list);
                changed = true;
            }
            return this;
        }

        public FeatureBatch remove(String dottedPath) {
            config.set(base(dottedPath), null);
            changed = true;
            return this;
        }
    }
}
