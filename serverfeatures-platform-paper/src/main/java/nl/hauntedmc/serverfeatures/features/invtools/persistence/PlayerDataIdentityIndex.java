package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
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
    private static final int MAX_USER_CACHE_BYTES = 4 * 1024 * 1024;

    private final Path playerDataDirectory;
    private final PlayerNameReader playerNameReader;
    private final List<Path> userCacheFiles;
    private final ConcurrentMap<String, UUID> observedPlayerIds = new ConcurrentHashMap<>();
    private final Object rebuildLock = new Object();

    private volatile Snapshot snapshot = Snapshot.empty();

    PlayerDataIdentityIndex(Path playerDataDirectory, PlayerNameReader playerNameReader) {
        this(playerDataDirectory, playerNameReader, List.of());
    }

    PlayerDataIdentityIndex(
            Path playerDataDirectory,
            PlayerNameReader playerNameReader,
            List<Path> userCacheFiles
    ) {
        this.playerDataDirectory = Objects.requireNonNull(playerDataDirectory, "playerDataDirectory");
        this.playerNameReader = Objects.requireNonNull(playerNameReader, "playerNameReader");
        this.userCacheFiles = List.copyOf(Objects.requireNonNull(userCacheFiles, "userCacheFiles"));
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
            return new Snapshot(
                    Map.of(),
                    directoryModifiedMillis(),
                    userCacheFingerprint(),
                    expiresAt()
            );
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
        loadUserCacheIdentities(indexed);
        return new Snapshot(
                Map.copyOf(indexed),
                directoryModifiedMillis(),
                userCacheFingerprint(),
                expiresAt()
        );
    }

    private boolean requiresRefresh(Snapshot current) throws IOException {
        return System.nanoTime() >= current.expiresAtNanos()
                || directoryModifiedMillis() != current.directoryModifiedMillis()
                || userCacheFingerprint() != current.userCacheFingerprint();
    }

    private void loadUserCacheIdentities(Map<String, UUID> indexed) throws IOException {
        for (Path userCacheFile : userCacheFiles) {
            if (!Files.isRegularFile(userCacheFile, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(userCacheFile)) {
                continue;
            }
            try {
                JsonElement root = JsonParser.parseString(readUserCache(userCacheFile));
                if (!root.isJsonArray()) {
                    continue;
                }
                for (JsonElement entry : root.getAsJsonArray()) {
                    UserCacheEntry identity = userCacheEntry(entry);
                    if (identity != null && hasPlayerData(identity.playerId())) {
                        indexed.putIfAbsent(normalize(identity.playerName()), identity.playerId());
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                // One malformed or concurrently replaced cache must not hide valid playerdata.
            }
        }
    }

    private static String readUserCache(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file, StandardOpenOption.READ)) {
            byte[] bytes = input.readNBytes(MAX_USER_CACHE_BYTES + 1);
            if (bytes.length > MAX_USER_CACHE_BYTES) {
                throw new IOException("User cache exceeds the safe read limit: " + file.getFileName());
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static UserCacheEntry userCacheEntry(JsonElement entry) {
        if (!entry.isJsonObject()) {
            return null;
        }
        JsonObject object = entry.getAsJsonObject();
        JsonElement name = object.get("name");
        JsonElement uuid = object.get("uuid");
        if (name == null || uuid == null || !name.isJsonPrimitive() || !uuid.isJsonPrimitive()) {
            return null;
        }
        String playerName = name.getAsString();
        if (playerName.isBlank()) {
            return null;
        }
        try {
            return new UserCacheEntry(UUID.fromString(uuid.getAsString()), playerName);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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

    private long userCacheFingerprint() throws IOException {
        long fingerprint = 1L;
        for (Path userCacheFile : userCacheFiles) {
            long modifiedMillis = Files.isRegularFile(userCacheFile, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(userCacheFile)
                    ? Files.getLastModifiedTime(userCacheFile, LinkOption.NOFOLLOW_LINKS).toMillis()
                    : -1L;
            fingerprint = 31L * fingerprint + modifiedMillis;
        }
        return fingerprint;
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

    private record UserCacheEntry(UUID playerId, String playerName) {
    }

    private record Snapshot(
            Map<String, UUID> playerIdsByName,
            long directoryModifiedMillis,
            long userCacheFingerprint,
            long expiresAtNanos
    ) {
        private static Snapshot empty() {
            return new Snapshot(Map.of(), Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE);
        }
    }
}
