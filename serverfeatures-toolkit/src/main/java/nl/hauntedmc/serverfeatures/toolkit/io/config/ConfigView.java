package nl.hauntedmc.serverfeatures.toolkit.io.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Typed, thread-safe CRUD view over a YAML file, optionally rooted at a base path. */
public class ConfigView {
    public final YamlFile file;
    protected final String base;

    public ConfigView(YamlFile file, String basePath) {
        this.file = Objects.requireNonNull(file, "file");
        this.base = basePath == null ? "" : basePath;
    }

    public ConfigView scope(String childBase) { return new ConfigView(file, base(childBase)); }

    protected String base(String key) {
        if (base.isEmpty()) return key == null || key.isEmpty() ? "" : key;
        return base + (key == null || key.isEmpty() ? "" : "." + key);
    }

    public Object get(String key) { return file.getRaw(base(key)); }
    public <T> T get(String key, Class<T> type) { return ConfigTypes.convert(get(key), type); }
    public <T> T get(String key, Class<T> type, T def) { return ConfigTypes.convertOrDefault(get(key), type, def); }
    public <T> List<T> getList(String key, Class<T> type) { return ConfigTypes.convertList(get(key), type); }

    public <T> List<T> getList(String key, Class<T> type, List<T> def) {
        try { return ConfigTypes.convertList(get(key), type); } catch (RuntimeException ignored) { return def; }
    }

    public <V> Map<String, V> getMapValues(String key, Class<V> type) {
        return ConfigTypes.convertMapValues(get(key), type);
    }

    public <V> Map<String, V> getMapValues(String key, Class<V> type, Map<String, V> def) {
        try { return ConfigTypes.convertMapValues(get(key), type); } catch (RuntimeException ignored) { return def; }
    }

    public ConfigNode node() { return ConfigNode.ofRaw(file.getRaw(base), base.isEmpty() ? "<root>" : base); }
    public ConfigNode node(String key) { return ConfigNode.ofRaw(get(key), base(key)); }
    public ConfigNode nodeAt(String path) { return node().getAt(path); }
    public <T> T getAt(String path, Class<T> type) { return node().getAt(path).asRequired(type); }
    public <T> T getAt(String path, Class<T> type, T def) { return node().getAt(path).as(type, def); }

    public void put(String path, Object value) { file.setRawAndSave(base(path), value); }
    public void remove(String path) { put(path, null); }

    public boolean putIfAbsent(String path, Object value) {
        String absolute = base(path);
        file.lock().writeLock().lock();
        try {
            CommentedConfigurationNode candidate = file.copyRootUnsafe();
            CommentedConfigurationNode node = candidate.node(YamlFile.splitPath(absolute));
            if (!node.virtual()) return false;
            node.set(value);
            file.commitCandidateUnsafe(candidate);
            return true;
        } catch (SerializationException exception) {
            throw new IllegalStateException("Unable to set absent configuration value: " + absolute, exception);
        } finally {
            file.lock().writeLock().unlock();
        }
    }

    public <T> T compute(String path, Class<T> type, UnaryOperator<T> update, Supplier<T> init)
            throws SerializationException {
        Objects.requireNonNull(update, "update");
        String absolute = base(path);
        file.lock().writeLock().lock();
        try {
            CommentedConfigurationNode candidate = file.copyRootUnsafe();
            CommentedConfigurationNode node = candidate.node(YamlFile.splitPath(absolute));
            T current;
            try { current = node.virtual() ? null : ConfigTypes.convert(node.get(Object.class), type); }
            catch (RuntimeException ignored) { current = null; }
            if (current == null && init != null) current = init.get();
            T next = Objects.requireNonNull(update.apply(current), "update returned null");
            node.set(next);
            file.commitCandidateUnsafe(candidate);
            return next;
        } finally {
            file.lock().writeLock().unlock();
        }
    }

    public void appendToList(String path, Object value) {
        String absolute = base(path);
        file.lock().writeLock().lock();
        try {
            CommentedConfigurationNode candidate = file.copyRootUnsafe();
            CommentedConfigurationNode node = candidate.node(YamlFile.splitPath(absolute));
            List<Object> list = mutableRawList(node);
            list.add(value);
            node.raw(list);
            file.commitCandidateUnsafe(candidate);
        } finally {
            file.lock().writeLock().unlock();
        }
    }

    public int removeFromList(String path, Predicate<Object> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        String absolute = base(path);
        file.lock().writeLock().lock();
        try {
            CommentedConfigurationNode candidate = file.copyRootUnsafe();
            CommentedConfigurationNode node = candidate.node(YamlFile.splitPath(absolute));
            List<Object> list = mutableRawList(node);
            int before = list.size();
            list.removeIf(predicate);
            int removed = before - list.size();
            if (removed > 0) {
                node.raw(list);
                file.commitCandidateUnsafe(candidate);
            }
            return removed;
        } finally {
            file.lock().writeLock().unlock();
        }
    }

    private static List<Object> mutableRawList(CommentedConfigurationNode node) {
        Object raw = node.raw();
        if (raw == null) return new ArrayList<>();
        if (raw instanceof List<?> list) return new ArrayList<>(list);
        throw new IllegalStateException("Configuration value is not a list: " + node.path());
    }

    public void batch(Consumer<Batch> transaction) {
        Objects.requireNonNull(transaction, "transaction");
        file.lock().writeLock().lock();
        try {
            CommentedConfigurationNode candidate = file.copyRootUnsafe();
            Batch batch = new Batch(candidate);
            transaction.accept(batch);
            if (batch.changed) file.commitCandidateUnsafe(candidate);
        } finally {
            file.lock().writeLock().unlock();
        }
    }

    public void mutateRaw(Consumer<CommentedConfigurationNode> mutator) { file.mutateAndSave(mutator); }

    public final class Batch {
        private final CommentedConfigurationNode root;
        private boolean changed;
        private Batch(CommentedConfigurationNode root) { this.root = root; }

        public Batch put(String path, Object value) throws SerializationException {
            root.node(YamlFile.splitPath(base(path))).set(value);
            changed = true;
            return this;
        }

        public Batch putIfAbsent(String path, Object value) throws SerializationException {
            CommentedConfigurationNode node = root.node(YamlFile.splitPath(base(path)));
            if (node.virtual()) {
                node.set(value);
                changed = true;
            }
            return this;
        }

        public <T> Batch compute(String path, Class<T> type, UnaryOperator<T> update, Supplier<T> init)
                throws SerializationException {
            CommentedConfigurationNode node = root.node(YamlFile.splitPath(base(path)));
            T current;
            try { current = node.virtual() ? null : ConfigTypes.convert(node.get(Object.class), type); }
            catch (RuntimeException ignored) { current = null; }
            if (current == null && init != null) current = init.get();
            node.set(Objects.requireNonNull(update.apply(current), "update returned null"));
            changed = true;
            return this;
        }

        public Batch appendToList(String path, Object value) throws SerializationException {
            CommentedConfigurationNode node = root.node(YamlFile.splitPath(base(path)));
            List<Object> list = mutableRawList(node);
            list.add(value);
            node.raw(list);
            changed = true;
            return this;
        }

        public Batch removeFromList(String path, Predicate<Object> predicate) throws SerializationException {
            CommentedConfigurationNode node = root.node(YamlFile.splitPath(base(path)));
            List<Object> list = mutableRawList(node);
            int before = list.size();
            list.removeIf(predicate);
            if (list.size() != before) {
                node.raw(list);
                changed = true;
            }
            return this;
        }

        public Batch remove(String path) throws SerializationException {
            root.node(YamlFile.splitPath(base(path))).set(null);
            changed = true;
            return this;
        }
    }

    public ConfigView root() { return base.isEmpty() ? this : new ConfigView(file, ""); }
    public ConfigView at(String path) { return path == null || path.isBlank() ? root() : new ConfigView(file, path); }
    public ConfigView globals() { return at("global"); }
    public ConfigView features() { return at("features"); }
}
