package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Resolves names from the playerdata files that actually exist on this server. Paper's profile
 * cache is not authoritative for local playerdata: it can contain a valid account whose data is
 * absent here, or a different UUID after proxy/online-mode changes.
 */
final class PlayerDataIdentityIndex {

    private static final long INDEX_TTL_NANOS = 30_000_000_000L;

    private final Path playerDataDirectory;
    private final PlayerNameReader playerNameReader;
    private final ConcurrentMap<String, UUID> observedPlayerIds = new ConcurrentHashMap<>();
    private final Object rebuildLock = new Object();

    private volatile Snapshot snapshot = Snapshot.empty();

    PlayerDataIdentityIndex(Path playerDataDirectory, PlayerNameReader playerNameReader) {
        this.playerDataDirectory = Objects.requireNonNull(playerDataDirectory, "playerDataDirectory");
        this.playerNameReader = Objects.requireNonNull(playerNameReader, "playerNameReader");
    }

    Optional<UUID> resolve(Optional<UUID> preferredPlayerId, String playerName) throws IOException {
        Objects.requireNonNull(preferredPlayerId, "preferredPlayerId");
        String normalizedName = normalize(playerName);

        UUID observedPlayerId = observedPlayerIds.get(normalizedName);
        if (observedPlayerId != null && hasPlayerData(observedPlayerId)) {
            return Optional.of(observedPlayerId);
        }

        if (preferredPlayerId.isPresent() && hasPlayerData(preferredPlayerId.get())) {
            return preferredPlayerId;
        }

        Snapshot current = snapshot;
        UUID indexedPlayerId = current.playerIdsByName().get(normalizedName);
        if (indexedPlayerId != null && hasPlayerData(indexedPlayerId)) {
            return Optional.of(indexedPlayerId);
        }
        if (!requiresRefresh(current)) {
            return Optional.empty();
        }

        synchronized (rebuildLock) {
            current = snapshot;
            indexedPlayerId = current.playerIdsByName().get(normalizedName);
            if (indexedPlayerId != null && hasPlayerData(indexedPlayerId)) {
                return Optional.of(indexedPlayerId);
            }
            if (requiresRefresh(current)) {
                snapshot = rebuild();
            }
            return Optional.ofNullable(snapshot.playerIdsByName().get(normalizedName));
        }
    }

    void remember(UUID playerId, String playerName) {
        if (playerId == null || playerName == null || playerName.isBlank()) {
            return;
        }
        String normalizedName = normalize(playerName);
        observedPlayerIds.entrySet().removeIf(entry ->
                entry.getValue().equals(playerId) && !entry.getKey().equals(normalizedName)
        );
        observedPlayerIds.put(normalizedName, playerId);
    }

    private Snapshot rebuild() throws IOException {
        if (!Files.isDirectory(playerDataDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return new Snapshot(Map.of(), directoryModifiedMillis(), expiresAt());
        }

        Map<String, Candidate> candidates = new HashMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(playerDataDirectory, "*.dat")) {
            for (Path file : files) {
                UUID playerId = playerId(file);
                if (playerId == null
                        || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(file)) {
                    continue;
                }
                String playerName;
                long modifiedMillis;
                try {
                    playerName = playerNameReader.read(file);
                    modifiedMillis = Files.getLastModifiedTime(
                            file,
                            LinkOption.NOFOLLOW_LINKS
                    ).toMillis();
                } catch (IOException | RuntimeException ignored) {
                    continue;
                }
                if (playerName == null || playerName.isBlank()) {
                    continue;
                }
                Candidate candidate = new Candidate(playerId, modifiedMillis);
                candidates.merge(
                        normalize(playerName),
                        candidate,
                        PlayerDataIdentityIndex::newest
                );
            }
        }

        Map<String, UUID> indexed = new HashMap<>();
        candidates.forEach((name, candidate) -> indexed.put(name, candidate.playerId()));
        return new Snapshot(Map.copyOf(indexed), directoryModifiedMillis(), expiresAt());
    }

    private boolean requiresRefresh(Snapshot current) throws IOException {
        return System.nanoTime() >= current.expiresAtNanos()
                || directoryModifiedMillis() != current.directoryModifiedMillis();
    }

    private boolean hasPlayerData(UUID playerId) {
        Path file = playerDataDirectory.resolve(playerId + ".dat").normalize();
        return file.getParent().equals(playerDataDirectory)
                && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(file);
    }

    private long directoryModifiedMillis() throws IOException {
        return Files.isDirectory(playerDataDirectory, LinkOption.NOFOLLOW_LINKS)
                ? Files.getLastModifiedTime(
                        playerDataDirectory,
                        LinkOption.NOFOLLOW_LINKS
                ).toMillis()
                : -1L;
    }

    private static long expiresAt() {
        return System.nanoTime() + INDEX_TTL_NANOS;
    }

    private static String normalize(String playerName) {
        return Objects.requireNonNull(playerName, "playerName").toLowerCase(Locale.ROOT);
    }

    private static UUID playerId(Path file) {
        String fileName = file.getFileName().toString();
        if (!fileName.endsWith(".dat")) {
            return null;
        }
        try {
            return UUID.fromString(fileName.substring(0, fileName.length() - 4));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Candidate newest(Candidate first, Candidate second) {
        return first.modifiedMillis() >= second.modifiedMillis() ? first : second;
    }

    @FunctionalInterface
    interface PlayerNameReader {
        String read(Path playerDataFile) throws IOException;
    }

    private record Candidate(UUID playerId, long modifiedMillis) {
    }

    private record Snapshot(
            Map<String, UUID> playerIdsByName,
            long directoryModifiedMillis,
            long expiresAtNanos
    ) {
        private static Snapshot empty() {
            return new Snapshot(Map.of(), Long.MIN_VALUE, Long.MIN_VALUE);
        }
    }
}
