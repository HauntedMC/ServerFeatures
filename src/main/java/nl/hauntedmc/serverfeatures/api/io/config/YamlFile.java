package nl.hauntedmc.serverfeatures.api.io.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Owns a single YAML file + its in-memory YamlConfiguration + a read/write lock.
 */
public final class YamlFile {
    private final Path path;
    private final Logger logger;
    private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
    private volatile FileConfiguration cfg;

    public YamlFile(Path path, Logger logger) {
        this.path = path;
        this.logger = logger;
        reload(); // initial load
    }

    public ReentrantReadWriteLock lock() { return rw; }

    /** Load from disk. */
    public void reload() {
        rw.writeLock().lock();
        try {
            this.cfg = YamlConfiguration.loadConfiguration(path.toFile());
        } finally {
            rw.writeLock().unlock();
        }
    }

    /** Persist to disk (caller holds write-intent via higher APIs). */
    void saveNow() {
        try {
            cfg.save(path.toFile());
        } catch (IOException e) {
            logger.severe("Could not save YAML '" + path + "': " + e.getMessage());
        }
    }

    /** Direct raw mutation with automatic save under write lock. */
    public void mutateAndSave(Consumer<FileConfiguration> mutator) {
        rw.writeLock().lock();
        try {
            mutator.accept(cfg);
            saveNow();
        } finally {
            rw.writeLock().unlock();
        }
    }

    // -------- Low-level access used by ConfigView --------
    Object getRaw(String absolutePath) {
        rw.readLock().lock();
        try { return cfg.get(absolutePath); }
        finally { rw.readLock().unlock(); }
    }

    boolean contains(String absolutePath) {
        rw.readLock().lock();
        try { return cfg.contains(absolutePath); }
        finally { rw.readLock().unlock(); }
    }

    void setRawAndSave(String absolutePath, Object value) {
        rw.writeLock().lock();
        try {
            cfg.set(absolutePath, value);
            saveNow();
        } finally {
            rw.writeLock().unlock();
        }
    }

    FileConfiguration snapshotUnsafe() { // guarded by external lock in ConfigView when used
        return cfg;
    }
}
