package nl.hauntedmc.serverfeatures.api.io.config;

import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.nio.file.*;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Central registry/factory for YAML files.
 * - Ensures parent dirs exist.
 * - Optionally copies defaults from plugin resources if the file is missing.
 * - Caches YamlFile per absolute path to share locks and memory across views.
 */
public final class ConfigService {
    private final Plugin plugin;
    private final Path dataDir;
    private final Logger logger;
    private final ConcurrentHashMap<Path, YamlFile> cache = new ConcurrentHashMap<>();

    public ConfigService(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dataDir = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
    }

    /**
     * Opens (and creates if missing) a YAML file at a path relative to the plugin data folder.
     * If copyDefaultsIfPresent is true and a resource exists in the JAR at that path,
     * the resource is copied; otherwise an empty file is created.
     */
    public YamlFile open(String relativePath, boolean copyDefaultsIfPresent) {
        Path abs = resolve(relativePath);

        return cache.computeIfAbsent(abs, p -> {
            try {
                Files.createDirectories(p.getParent());
                if (Files.notExists(p)) {
                    if (copyDefaultsIfPresent) {
                        try (InputStream in = plugin.getResource(relativePath)) {
                            if (in != null) {
                                // Bukkit provides a helper, but we want to control creation and logging:
                                plugin.saveResource(relativePath, false);
                            } else {
                                Files.createFile(p);
                            }
                        }
                    } else {
                        Files.createFile(p);
                    }
                }
                if (!Files.isRegularFile(p)) {
                    throw new IllegalStateException("YAML path is not a regular file: " + p);
                }
                return new YamlFile(p, logger);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to open YAML file: " + p, e);
            }
        });
    }

    /**
     * Opens a YAML file only when it already exists.
     */
    public Optional<YamlFile> openExisting(String relativePath) {
        Path abs = resolve(relativePath);
        if (Files.notExists(abs)) {
            return Optional.empty();
        }
        return Optional.of(open(relativePath, false));
    }

    public boolean exists(String relativePath) {
        return Files.exists(resolve(relativePath));
    }

    /**
     * Resolves a relative config path without allowing it to escape the plugin data directory.
     */
    public Path resolve(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        if (relativePath.isBlank()) {
            throw new IllegalArgumentException("Config path must not be blank.");
        }
        Path relative = Path.of(relativePath);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("Config path must be relative: " + relativePath);
        }
        Path abs = dataDir.resolve(relative).normalize();
        if (!abs.startsWith(dataDir)) {
            throw new IllegalArgumentException("Config path escapes data directory: " + relativePath);
        }
        return abs;
    }

    /** Convenience: opens a root-scoped view on a YAML file. */
    public ConfigView view(String relativePath, boolean copyDefaultsIfPresent) {
        return new ConfigView(open(relativePath, copyDefaultsIfPresent), "");
    }

    /** Convenience: opens a scoped view at basePath inside a YAML file. */
    public ConfigView view(String relativePath, boolean copyDefaultsIfPresent, String basePath) {
        return new ConfigView(open(relativePath, copyDefaultsIfPresent), basePath);
    }
}
