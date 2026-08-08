package nl.hauntedmc.serverfeatures.toolkit.io.config;

import nl.hauntedmc.serverfeatures.toolkit.ToolkitContext;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Central registry/factory for YAML files shared by the Paper runtime and feature implementations. */
public final class ConfigService {
    private final Path dataDir;
    private final Logger logger;
    private final ClassLoader resources;
    private final ConcurrentHashMap<Path, YamlFile> cache = new ConcurrentHashMap<>();

    public ConfigService(ToolkitContext context) {
        this(Objects.requireNonNull(context.getDataDirectory(), "dataDirectory"),
                Objects.requireNonNull(context.getLogger(), "logger"),
                context.getResourceClassLoader());
    }

    public ConfigService(Path dataDir, Logger logger, ClassLoader resources) {
        this.dataDir = Objects.requireNonNull(dataDir, "dataDir").toAbsolutePath().normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
        this.resources = resources == null ? ConfigService.class.getClassLoader() : resources;
    }

    public YamlFile open(String relativePath, boolean copyDefaultsIfPresent) {
        Path absolute = resolve(relativePath);
        return cache.computeIfAbsent(absolute, path -> {
            try {
                Files.createDirectories(path.getParent());
                if (Files.notExists(path)) {
                    if (copyDefaultsIfPresent) {
                        try (InputStream input = resources.getResourceAsStream(relativePath)) {
                            if (input != null) {
                                Files.copy(input, path);
                                logger.info("[ServerFeatures] Copied default resource '{}'", relativePath);
                            } else {
                                Files.createFile(path);
                                logger.info("[ServerFeatures] Created empty file '{}'", relativePath);
                            }
                        }
                    } else {
                        Files.createFile(path);
                        logger.info("[ServerFeatures] Created empty file '{}'", relativePath);
                    }
                }
                return new YamlFile(path, logger);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to open YAML file: " + path, exception);
            }
        });
    }

    public Optional<YamlFile> openExisting(String relativePath) {
        Path absolute = resolve(relativePath);
        if (Files.notExists(absolute)) return Optional.empty();
        return Optional.of(open(relativePath, false));
    }

    public boolean exists(String relativePath) { return Files.exists(resolve(relativePath)); }

    public Path resolve(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        Path absolute = dataDir.resolve(relativePath).normalize();
        if (!absolute.startsWith(dataDir)) {
            throw new IllegalArgumentException("Config path escapes data directory: " + relativePath);
        }
        return absolute;
    }

    public ConfigView view(String relativePath, boolean copyDefaultsIfPresent) {
        return new ConfigView(open(relativePath, copyDefaultsIfPresent), "");
    }

    public ConfigView view(String relativePath, boolean copyDefaultsIfPresent, String basePath) {
        return new ConfigView(open(relativePath, copyDefaultsIfPresent), basePath);
    }
}
