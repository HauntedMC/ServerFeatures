package nl.hauntedmc.serverfeatures.features.limitspawners.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.TrackedSpawnerMob;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Atomic JSON snapshot store for entities that can be unloaded while their source spawner remains active.
 */
public final class SpawnerMobStore {

    private static final int SCHEMA_VERSION = 1;

    private final Path file;
    private final FeatureLogger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public SpawnerMobStore(Path file, FeatureLogger logger) {
        this.file = Objects.requireNonNull(file, "file");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public List<TrackedSpawnerMob> load() {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Snapshot snapshot = gson.fromJson(reader, Snapshot.class);
            if (snapshot == null) {
                return List.of();
            }
            if (snapshot.schemaVersion() != SCHEMA_VERSION) {
                throw new IllegalStateException(
                        "Unsupported LimitSpawners registry schema " + snapshot.schemaVersion()
                );
            }

            List<TrackedSpawnerMob> records = snapshot.records() == null
                    ? List.of()
                    : snapshot.records();
            List<TrackedSpawnerMob> validated = new ArrayList<>(records.size());
            for (TrackedSpawnerMob record : records) {
                if (record != null) {
                    validated.add(record);
                }
            }
            return List.copyOf(validated);
        } catch (IOException | RuntimeException exception) {
            quarantineCorruptFile(exception);
            return List.of();
        }
    }

    public void save(Collection<TrackedSpawnerMob> records) {
        Objects.requireNonNull(records, "records");
        Path parent = Objects.requireNonNull(file.getParent(), "file.parent");

        try {
            Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
            try {
                try (BufferedWriter writer = Files.newBufferedWriter(
                        temporary,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING
                )) {
                    gson.toJson(new Snapshot(SCHEMA_VERSION, List.copyOf(records)), writer);
                }

                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    channel.force(true);
                }

                moveAtomically(temporary, file);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist LimitSpawners registry to " + file, exception);
        }
    }

    Path file() {
        return file;
    }

    private void quarantineCorruptFile(Exception exception) {
        Path quarantine = file.resolveSibling(
                file.getFileName() + ".corrupt-" + System.currentTimeMillis()
        );
        try {
            Files.move(file, quarantine, StandardCopyOption.REPLACE_EXISTING);
            logger.log(
                    Level.SEVERE,
                    "Could not read tracked mob registry; moved it to " + quarantine,
                    exception
            );
        } catch (IOException moveFailure) {
            exception.addSuppressed(moveFailure);
            logger.log(
                    Level.SEVERE,
                    "Could not read or quarantine tracked mob registry " + file,
                    exception
            );
        }
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record Snapshot(int schemaVersion, List<TrackedSpawnerMob> records) {
    }
}
