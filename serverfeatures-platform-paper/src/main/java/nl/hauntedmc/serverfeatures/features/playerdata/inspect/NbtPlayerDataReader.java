package nl.hauntedmc.serverfeatures.features.playerdata.inspect;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.NBTType;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import nl.hauntedmc.serverfeatures.features.playerdata.model.PlayerDataEntry;
import nl.hauntedmc.serverfeatures.framework.playerdata.PlayerDataFiles;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * Bounded, read-only access to Paper player .dat files for staff diagnostics.
 */
public final class NbtPlayerDataReader {

    private static final long INDEX_TTL_NANOS = 30_000_000_000L;
    private static final int MAX_PATH_LENGTH = 160;
    private static final int MAX_PATH_DEPTH = 12;

    private final Path levelDirectory;
    private final Path playerDataDirectory;
    private final int maxCompressedBytes;
    private final int maxDecompressedBytes;
    private volatile NameIndex nameIndex = new NameIndex(Map.of(), 0L);

    public NbtPlayerDataReader(
            Path levelDirectory,
            int maxCompressedBytes,
            int maxDecompressedBytes
    ) {
        this.levelDirectory = Objects.requireNonNull(levelDirectory, "levelDirectory")
                .toAbsolutePath()
                .normalize();
        this.playerDataDirectory = PlayerDataFiles.dataDirectory(this.levelDirectory);
        if (maxCompressedBytes < 1 || maxDecompressedBytes < maxCompressedBytes) {
            throw new IllegalArgumentException("Invalid playerdata read limits");
        }
        this.maxCompressedBytes = maxCompressedBytes;
        this.maxDecompressedBytes = maxDecompressedBytes;
    }

    public Optional<ResolvedPlayerData> resolve(String input, UUID preferredPlayerId) throws IOException {
        String requested = input == null ? "" : input.trim();
        if (requested.isEmpty()) {
            return Optional.empty();
        }

        UUID parsed = parseUuid(requested);
        if (parsed != null && hasPlayerData(parsed)) {
            return Optional.of(resolveKnownId(parsed, requested));
        }
        if (preferredPlayerId != null && hasPlayerData(preferredPlayerId)) {
            return Optional.of(resolveKnownId(preferredPlayerId, requested));
        }

        String normalized = requested.toLowerCase(Locale.ROOT);
        NameIndex current = nameIndex;
        if (System.nanoTime() >= current.expiresAtNanos()) {
            synchronized (this) {
                current = nameIndex;
                if (System.nanoTime() >= current.expiresAtNanos()) {
                    current = rebuildNameIndex();
                    nameIndex = current;
                }
            }
        }
        UUID playerId = current.playerIdsByName().get(normalized);
        return playerId == null ? Optional.empty() : Optional.of(resolveKnownId(playerId, requested));
    }

    public Inspection inspectOverview(ResolvedPlayerData target, int maxValueLength) throws IOException {
        LoadedData loaded = load(target.playerId());
        List<PlayerDataEntry> entries = new ArrayList<>();
        entries.add(entry("uuid", "uuid", target.playerId().toString(), maxValueLength));
        entries.add(entry("name", "string", lastKnownName(loaded.root()).orElse(target.playerName()), maxValueLength));
        entries.add(entry("data-version", "int", integerValue(loaded.root(), "DataVersion"), maxValueLength));
        entries.add(entry("compressed-size", "bytes", loaded.compressedBytes().length, maxValueLength));
        entries.add(entry("last-modified", "instant", Files.getLastModifiedTime(loaded.file()).toInstant(), maxValueLength));
        entries.add(entry("top-level-tags", "count", loaded.root().getKeys().size(), maxValueLength));
        ReadableNBT pdc = bukkitValues(loaded.root());
        entries.add(entry("pdc-keys", "count", pdc == null ? 0 : pdc.getKeys().size(), maxValueLength));
        entries.add(entry(
                "serverfeatures-settings",
                "count",
                pdc == null ? 0 : pdc.getKeys().stream().filter(NbtPlayerDataReader::isServerFeaturesKey).count(),
                maxValueLength
        ));
        return new Inspection(target, "overview", List.copyOf(entries), entries.size());
    }

    public Inspection inspectSettings(
            ResolvedPlayerData target,
            int maxEntries,
            int maxValueLength
    ) throws IOException {
        ReadableNBT pdc = bukkitValues(load(target.playerId()).root());
        return inspectCompound(target, "settings", pdc, NbtPlayerDataReader::isServerFeaturesKey,
                maxEntries, maxValueLength);
    }

    public Inspection inspectPdc(
            ResolvedPlayerData target,
            int maxEntries,
            int maxValueLength
    ) throws IOException {
        ReadableNBT pdc = bukkitValues(load(target.playerId()).root());
        return inspectCompound(target, "pdc", pdc, ignored -> true, maxEntries, maxValueLength);
    }

    public Inspection inspectNbt(
            ResolvedPlayerData target,
            String path,
            int maxEntries,
            int maxValueLength
    ) throws IOException {
        ReadableNBT root = load(target.playerId()).root();
        String normalizedPath = path == null ? "" : path.trim();
        if (normalizedPath.isEmpty()) {
            return inspectCompound(target, "nbt:<root>", root, ignored -> true, maxEntries, maxValueLength);
        }
        if (normalizedPath.length() > MAX_PATH_LENGTH) {
            throw new IOException("NBT path is too long");
        }
        String[] parts = normalizedPath.split("\\.", -1);
        if (parts.length > MAX_PATH_DEPTH || Arrays.stream(parts).anyMatch(String::isBlank)) {
            throw new IOException("NBT path is invalid");
        }

        ReadableNBT current = root;
        for (int index = 0; index < parts.length - 1; index++) {
            String part = parts[index];
            if (!current.hasTag(part, NBTType.NBTTagCompound)) {
                throw new IOException("NBT path is not a compound at '" + part + "'");
            }
            current = current.getCompound(part);
            if (current == null) {
                throw new IOException("NBT path does not exist");
            }
        }

        String leaf = parts[parts.length - 1];
        if (!current.hasTag(leaf)) {
            throw new IOException("NBT path does not exist: " + normalizedPath);
        }
        if (current.hasTag(leaf, NBTType.NBTTagCompound)) {
            ReadableNBT compound = current.getCompound(leaf);
            return inspectCompound(
                    target,
                    "nbt:" + normalizedPath,
                    compound,
                    ignored -> true,
                    maxEntries,
                    maxValueLength
            );
        }
        return new Inspection(
                target,
                "nbt:" + normalizedPath,
                List.of(readEntry(current, leaf, maxValueLength)),
                1
        );
    }

    private Inspection inspectCompound(
            ResolvedPlayerData target,
            String section,
            ReadableNBT compound,
            java.util.function.Predicate<String> filter,
            int maxEntries,
            int maxValueLength
    ) {
        if (compound == null) {
            return new Inspection(target, section, List.of(), 0);
        }
        List<String> keys = compound.getKeys().stream()
                .filter(filter)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        List<PlayerDataEntry> entries = keys.stream()
                .limit(maxEntries)
                .map(key -> readEntry(compound, key, maxValueLength))
                .toList();
        return new Inspection(target, section, entries, keys.size());
    }

    private NameIndex rebuildNameIndex() throws IOException {
        if (!Files.isDirectory(playerDataDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return new NameIndex(Map.of(), System.nanoTime() + INDEX_TTL_NANOS);
        }
        Map<String, Candidate> candidates = new HashMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(playerDataDirectory, "*.dat")) {
            for (Path file : files) {
                UUID playerId = playerId(file);
                if (playerId == null || !safeRegularFile(file)) {
                    continue;
                }
                try {
                    LoadedData loaded = loadFile(file);
                    Optional<String> name = lastKnownName(loaded.root());
                    if (name.isEmpty() || name.get().isBlank()) {
                        continue;
                    }
                    Candidate candidate = new Candidate(
                            playerId,
                            Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toMillis()
                    );
                    candidates.merge(
                            name.get().toLowerCase(Locale.ROOT),
                            candidate,
                            (left, right) -> left.modifiedMillis() >= right.modifiedMillis() ? left : right
                    );
                } catch (IOException | RuntimeException ignored) {
                    // One corrupt/unsupported record must not prevent inspection of other players.
                }
            }
        }
        Map<String, UUID> indexed = new HashMap<>();
        candidates.forEach((name, candidate) -> indexed.put(name, candidate.playerId()));
        return new NameIndex(Map.copyOf(indexed), System.nanoTime() + INDEX_TTL_NANOS);
    }

    private ResolvedPlayerData resolveKnownId(UUID playerId, String fallbackName) throws IOException {
        LoadedData loaded = load(playerId);
        String name = lastKnownName(loaded.root()).orElse(fallbackName);
        return new ResolvedPlayerData(playerId, name == null || name.isBlank() ? playerId.toString() : name);
    }

    private LoadedData load(UUID playerId) throws IOException {
        return loadFile(PlayerDataFiles.playerFile(levelDirectory, playerId));
    }

    private LoadedData loadFile(Path file) throws IOException {
        byte[] bytes = readPlayerData(file);
        try {
            return new LoadedData(file, bytes, NBT.readNBT(new ByteArrayInputStream(bytes)));
        } catch (RuntimeException | LinkageError exception) {
            throw new IOException("Could not parse playerdata file " + file.getFileName(), exception);
        }
    }

    private byte[] readPlayerData(Path file) throws IOException {
        if (!safeRegularFile(file)) {
            throw new IOException("Playerdata file is missing or unsafe: " + file.getFileName());
        }
        try (InputStream input = Files.newInputStream(
                file,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS
        )) {
            byte[] bytes = input.readNBytes(maxCompressedBytes + 1);
            if (bytes.length > maxCompressedBytes) {
                throw new IOException("Playerdata file exceeds the safe compressed read limit");
            }
            validateDecompressedSize(bytes, file);
            return bytes;
        }
    }

    private void validateDecompressedSize(byte[] bytes, Path file) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = gzip.read(buffer)) >= 0) {
                total = Math.addExact(total, count);
                if (total > maxDecompressedBytes) {
                    throw new IOException("Playerdata expands beyond the safe read limit: " + file.getFileName());
                }
            }
        } catch (ArithmeticException exception) {
            throw new IOException("Playerdata expands beyond the safe read limit", exception);
        }
    }

    private boolean hasPlayerData(UUID playerId) {
        return safeRegularFile(PlayerDataFiles.playerFile(levelDirectory, playerId));
    }

    private static boolean safeRegularFile(Path file) {
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file);
    }

    private static UUID parseUuid(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static UUID playerId(Path file) {
        String name = file.getFileName().toString();
        if (!name.endsWith(".dat")) {
            return null;
        }
        return parseUuid(name.substring(0, name.length() - 4));
    }

    private static Optional<String> lastKnownName(ReadableNBT root) {
        if (!root.hasTag("bukkit", NBTType.NBTTagCompound)) {
            return Optional.empty();
        }
        ReadableNBT bukkit = root.getCompound("bukkit");
        if (bukkit == null || !bukkit.hasTag("lastKnownName", NBTType.NBTTagString)) {
            return Optional.empty();
        }
        return Optional.ofNullable(bukkit.getString("lastKnownName"));
    }

    private static ReadableNBT bukkitValues(ReadableNBT root) {
        return root.hasTag("BukkitValues", NBTType.NBTTagCompound)
                ? root.getCompound("BukkitValues")
                : null;
    }

    private static boolean isServerFeaturesKey(String key) {
        return key != null && key.regionMatches(true, 0, "serverfeatures:", 0, "serverfeatures:".length());
    }

    private static Object integerValue(ReadableNBT root, String key) {
        return root.hasTag(key, NBTType.NBTTagInt) ? root.getInteger(key) : "<missing>";
    }

    private static PlayerDataEntry readEntry(ReadableNBT compound, String key, int maxValueLength) {
        NBTType type = compound.getType(key);
        String typeName = type == null ? "unknown" : type.name();
        Object value;
        if (type == NBTType.NBTTagByte) {
            byte raw = compound.getByte(key);
            value = (raw == 0 || raw == 1) ? raw + (raw == 1 ? " (true)" : " (false)") : raw;
        } else if (type == NBTType.NBTTagShort) {
            value = compound.getShort(key);
        } else if (type == NBTType.NBTTagInt) {
            value = compound.getInteger(key);
        } else if (type == NBTType.NBTTagLong) {
            value = compound.getLong(key);
        } else if (type == NBTType.NBTTagFloat) {
            value = compound.getFloat(key);
        } else if (type == NBTType.NBTTagDouble) {
            value = compound.getDouble(key);
        } else if (type == NBTType.NBTTagString) {
            value = compound.getString(key);
        } else if (type == NBTType.NBTTagByteArray) {
            value = Arrays.toString(compound.getByteArray(key));
        } else if (type == NBTType.NBTTagIntArray) {
            value = Arrays.toString(compound.getIntArray(key));
        } else if (type == NBTType.NBTTagLongArray) {
            value = Arrays.toString(compound.getLongArray(key));
        } else if (type == NBTType.NBTTagCompound) {
            ReadableNBT nested = compound.getCompound(key);
            value = "{" + (nested == null ? 0 : nested.getKeys().size()) + " keys}";
        } else if (type == NBTType.NBTTagList) {
            value = "<list>";
        } else {
            value = "<not decoded>";
        }
        return entry(key, typeName, value, maxValueLength);
    }

    private static PlayerDataEntry entry(String key, String type, Object value, int maxValueLength) {
        return new PlayerDataEntry(key, type, truncate(String.valueOf(value), maxValueLength));
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }

    public record ResolvedPlayerData(UUID playerId, String playerName) {
        public ResolvedPlayerData {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(playerName, "playerName");
        }
    }

    public record Inspection(
            ResolvedPlayerData target,
            String section,
            List<PlayerDataEntry> entries,
            int totalEntries
    ) {
        public Inspection {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(section, "section");
            entries = List.copyOf(entries);
            if (totalEntries < entries.size()) {
                throw new IllegalArgumentException("totalEntries cannot be smaller than entries");
            }
        }
    }

    private record LoadedData(Path file, byte[] compressedBytes, ReadWriteNBT root) {
    }

    private record Candidate(UUID playerId, long modifiedMillis) {
    }

    private record NameIndex(Map<String, UUID> playerIdsByName, long expiresAtNanos) {
    }
}
