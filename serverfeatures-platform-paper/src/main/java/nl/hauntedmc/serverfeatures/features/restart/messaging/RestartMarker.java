package nl.hauntedmc.serverfeatures.features.restart.messaging;

/** Persistent state required to correlate PREPARE before shutdown with READY after startup. */
public record RestartMarker(
        String restartId,
        String serverName,
        long createdAtEpochMillis,
        long expiresAtEpochMillis,
        long reconnectDelayMillis,
        long playerIntervalMillis
) {
}
