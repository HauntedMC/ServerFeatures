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
import java.util.logging.Logger;

/**
 * Resolves command names to the strongest UUID candidate for playerdata on this server.
 *
 * <p>DataRegistry is the primary authority for current usernames and forwarded UUIDs. Local
 * connection observations, playerdata metadata, and Paper's user cache remain conservative fallback
 * sources for legacy files that predate the canonical registry or survived a UUID-mode migration.</p>
 */
final class PlayerDataIdentityIndex {

    private static final Logger LOGGER = Logger.getLogger("ServerFeatures");
    private static final long INDEX_TTL_NANOS = 30_000_000_000L;
    private static final int MAX_USER_CACHE_BYTES = 4 * 1024 * 1024;

    private final Path playerDataDirectory;
    private final PlayerNameReader playerNameReader;
    private final List<Path> userCacheFiles;
    private final CanonicalPlayerIdentityLookup canonicalIdentityLookup;
    private final ConcurrentMap<String, UUID> observedPlayerIds = new ConcurrentHashMap<>();
    private final Object rebuildLock = new Object();

    private volatile Snapshot snapshot = Snapshot.empty();

    PlayerDataIdentityIndex(Path playerDataDirectory, PlayerNameReader playerNameReader) {
        this(
                playerDataDirectory,
                playerNameReader,
                List.of(),
                CanonicalPlayerIdentityLookup.none()
        );
    }

    PlayerDataIdentityIndex(
            Path playerDataDirectory,
            PlayerNameReader playerNameReader,
            List<Path> userCacheFiles
    ) {
        this(
                playerDataDirectory,
                playerNameReader,
                userCacheFiles,
                DataRegistryPlayerIdentityLookup.forServerFeatures()
        );
    }

    PlayerDataIdentityIndex(
            Path playerDataDirectory,
            PlayerNameReader playerNameReader,
            List<Path> userCacheFiles,
            CanonicalPlayerIdentityLookup canonicalIdentityLookup
    ) {
        this.playerDataDirectory = Objects.requireNonNull(playerDataDirectory, "playerDataDirectory");
        this.playerNameReader = Objects.requireNonNull(playerNameReader, "playerNameReader");
        this.userCacheFiles = List.copyOf(Objects.requireNonNull(userCacheFiles, "userCacheFiles"));
        this.canonicalIdentityLookup = Objects.requireNonNull(
                canonicalIdentityLookup,
                "canonicalIdentityLookup"
        );
    }

    Optional<UUID> resolve(Optional<UUID> preferredPlayerId, String playerName) throws IOException {
        Objects.requireNonNull(preferredPlayerId, "preferredPlayerId");
        String normalizedName = normalize(playerName);

        UUID observedPlayerId = observedPlayerIds.get(normalizedName);
        if (observedPlayerId != null) {
            /*
             * A UUID observed from a real connection remains authoritative while Paper completes
             * the quit sequence. The playerdata file may not exist yet when PlayerQuitEvent fires;
             * the service's bounded appearance retry must therefore receive this UUID immediately.
             */
            if (!hasPlayerData(observedPlayerId)) {
                logPendingCandidate("observed-connection", playerName, observedPlayerId);
            }
            return Optional.of(observedPlayerId);
        }

        UUID pendingCanonicalPlayerId = null;
        Optional<CanonicalPlayerIdentityLookup.Identity> canonicalIdentity =
                canonicalIdentityLookup.find(playerName);
        if (canonicalIdentity.isPresent()) {
            CanonicalPlayerIdentityLookup.Identity identity = canonicalIdentity.get();
            if (hasPlayerData(identity.playerId())) {
                remember(identity.playerId(), identity.playerName());
                return Optional.of(identity.playerId());
            }
            pendingCanonicalPlayerId = identity.playerId();
        }

        if (preferredPlayerId.isPresent()
                && matchesPlayerName(preferredPlayerId.get(), normalizedName)) {
            return preferredPlayerId;
        }

        Snapshot current = snapshot;
        UUID indexedPlayerId = current.playerIdsByName().get(normalizedName);
        if (indexedPlayerId != null && hasPlayerData(indexedPlayerId)) {
            return Optional.of(indexedPlayerId);
        }
        if (!requiresRefresh(current)) {
            return finishResolution(
                    canonicalIdentity,
                    pendingCanonicalPlayerId,
                    preferredPlayerId,
                    playerName
            );
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
            indexedPlayerId = snapshot.playerIdsByName().get(normalizedName);
            if (indexedPlayerId != null) {
                return Optional.of(indexedPlayerId);
            }
            return finishResolution(
                    canonicalIdentity,
                    pendingCanonicalPlayerId,
                    preferredPlayerId,
                    playerName
            );
        }
    }

    private Optional<UUID> finishResolution(
            Optional<CanonicalPlayerIdentityLookup.Identity> canonicalIdentity,
            UUID pendingCanonicalPlayerId,
            Optional<UUID> preferredPlayerId,
            String playerName
    ) {
        if (pendingCanonicalPlayerId != null && canonicalIdentity.isPresent()) {
            CanonicalPlayerIdentityLookup.Identity identity = canonicalIdentity.get();
            remember(identity.playerId(), identity.playerName());
            logPendingCandidate("dataregistry", playerName, pendingCanonicalPlayerId);
            return Optional.of(pendingCanonicalPlayerId);
        }
        LOGGER.warning(() -> "[InvTools] Could not resolve offline player identity: name="
                + playerName
                + ", paperCachedUuid=" + preferredPlayerId.map(UUID::toString).orElse("none")
                + ", " + describeDirectory());
        return Optional.empty();
    }

    private void logPendingCandidate(String source, String playerName, UUID playerId) {
        LOGGER.warning(() -> "[InvTools] Resolved an authoritative offline UUID while its playerdata "
                + "file is not visible yet; the bounded logout retry will continue: source=" + source
                + ", name=" + playerName
                + ", uuid=" + playerId
                + ", " + describePlayerFile(playerId));
    }

    private String describeDirectory() {
        return "playerDataDirectory=" + playerDataDirectory.toAbsolutePath().normalize()
                + ", directoryExists=" + Files.exists(playerDataDirectory, LinkOption.NOFOLLOW_LINKS)
                + ", directoryIsDirectory="
                + Files.isDirectory(playerDataDirectory, LinkOption.NOFOLLOW_LINKS)
                + ", directoryReadable=" + Files.isReadable(playerDataDirectory);
    }

    private String describePlayerFile(UUID playerId) {
        Path file = playerDataDirectory.resolve(playerId + ".dat").normalize();
        return describeDirectory()
                + ", expectedFile=" + file.toAbsolutePath().normalize()
                + ", fileExists=" + Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                + ", fileIsRegular=" + Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                + ", fileIsSymlink=" + Files.isSymbolicLink(file)
                + ", fileReadable=" + Files.isReadable(file);
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

    private void loadUserCacheIdentities(Map<String, UUID> indexed) {
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

    private boolean matchesPlayerName(UUID playerId, String normalizedName) {
        if (!hasPlayerData(playerId)) {
            return false;
        }
        try {
            String storedName = playerNameReader.read(playerDataDirectory.resolve(playerId + ".dat"));
            return storedName != null && normalize(storedName).equals(normalizedName);
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private long directoryModifiedMillis() throws IOException {
        return Files.isDirectory(playerDataDirectory, LinkOption.NOFOLLOW_LINKS)
                ? Files.getLastModifiedTime(
                        playerDataDirectory,
                        LinkOption.NOFOLLOW_LINKS
                ).toMillis()
                : -1L;
    }

    private long userCacheFingerprint() {
        long fingerprint = 1L;
        for (Path userCacheFile : userCacheFiles) {
            long modifiedMillis = userCacheModifiedMillis(userCacheFile);
            fingerprint = 31L * fingerprint + modifiedMillis;
        }
        return fingerprint;
    }

    private static long userCacheModifiedMillis(Path userCacheFile) {
        try {
            return Files.isRegularFile(userCacheFile, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(userCacheFile)
                    ? Files.getLastModifiedTime(userCacheFile, LinkOption.NOFOLLOW_LINKS).toMillis()
                    : -1L;
        } catch (IOException | SecurityException ignored) {
            // The cache is only a name-discovery hint; its transient failure must not fail InvTools.
            return -1L;
        }
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
