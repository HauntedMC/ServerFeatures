package nl.hauntedmc.serverfeatures.api.io.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.logging.Level;
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
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
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

    /**
     * Persist to disk using a same-directory temporary file and atomic replacement when supported.
     *
     * <p>Persistence failures are deliberately propagated so callers cannot mistake a failed write
     * for durable state.</p>
     */
    void saveNow() {
        Path parent = path.getParent();
        Path temporary = null;
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            temporary = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
            Files.writeString(
                    temporary,
                    cfg.saveToString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            try {
                Files.move(
                        temporary,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // Keep the in-memory representation consistent with the last durable version.
            this.cfg = YamlConfiguration.loadConfiguration(path.toFile());
            logger.log(Level.SEVERE, "Could not save YAML '" + path + "'.", e);
            throw new IllegalStateException("Could not save YAML '" + path + "'.", e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupError) {
                    logger.log(Level.WARNING, "Could not remove temporary YAML file '" + temporary + "'.", cleanupError);
                }
            }
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
