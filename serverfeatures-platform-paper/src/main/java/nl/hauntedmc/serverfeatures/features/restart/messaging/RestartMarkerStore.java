package nl.hauntedmc.serverfeatures.features.restart.messaging;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;

/** Small atomic properties-backed store for the in-flight restart marker. */
public final class RestartMarkerStore {

    private final Path markerPath;

    public RestartMarkerStore(Path markerPath) {
        this.markerPath = markerPath;
    }

    public synchronized void save(RestartMarker marker) throws IOException {
        Path parent = markerPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Properties properties = new Properties();
        properties.setProperty("restart_id", marker.restartId());
        properties.setProperty("server_name", marker.serverName());
        properties.setProperty("created_at_epoch_millis", Long.toString(marker.createdAtEpochMillis()));
        properties.setProperty("expires_at_epoch_millis", Long.toString(marker.expiresAtEpochMillis()));
        properties.setProperty("reconnect_delay_millis", Long.toString(marker.reconnectDelayMillis()));
        properties.setProperty("player_interval_millis", Long.toString(marker.playerIntervalMillis()));

        Path temporary = markerPath.resolveSibling(markerPath.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "ServerFeatures restart autoreconnect marker");
        }
        try {
            Files.move(
                    temporary,
                    markerPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, markerPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public synchronized Optional<RestartMarker> load() throws IOException {
        if (!Files.isRegularFile(markerPath)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(markerPath)) {
            properties.load(input);
        }
        try {
            return Optional.of(new RestartMarker(
                    required(properties, "restart_id"),
                    required(properties, "server_name"),
                    Long.parseLong(required(properties, "created_at_epoch_millis")),
                    Long.parseLong(required(properties, "expires_at_epoch_millis")),
                    Long.parseLong(required(properties, "reconnect_delay_millis")),
                    Long.parseLong(required(properties, "player_interval_millis"))
            ));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid restart autoreconnect marker: " + exception.getMessage(), exception);
        }
    }

    public synchronized void delete() throws IOException {
        Files.deleteIfExists(markerPath);
        Files.deleteIfExists(markerPath.resolveSibling(markerPath.getFileName() + ".tmp"));
    }

    Path path() {
        return markerPath;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing " + key);
        }
        return value.trim();
    }
}
