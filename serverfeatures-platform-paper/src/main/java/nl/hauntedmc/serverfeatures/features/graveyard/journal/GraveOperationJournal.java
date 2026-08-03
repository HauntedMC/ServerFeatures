package nl.hauntedmc.serverfeatures.features.graveyard.journal;

import nl.hauntedmc.serverfeatures.api.graveyard.GraveStatus;
import nl.hauntedmc.serverfeatures.features.graveyard.model.Grave;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GraveLocation;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GravePlacementType;
import nl.hauntedmc.serverfeatures.features.graveyard.persistence.EncodedGravePayload;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Per-operation atomic journal. Each transition rewrites one independent record, so a damaged record
 * can be quarantined without making unrelated captures or claims unreadable.
 */
public final class GraveOperationJournal {
    private static final String SUFFIX = ".properties";

    private final Path captureDirectory;
    private final Path claimDirectory;
    private final Path corruptDirectory;
    private final int maximumRecordBytes;

    public GraveOperationJournal(Path featureDirectory, int maximumRecordBytes) throws IOException {
        Path root = featureDirectory.resolve("local").resolve("journal");
        captureDirectory = root.resolve("capture");
        claimDirectory = root.resolve("claim");
        corruptDirectory = root.resolve("corrupt");
        this.maximumRecordBytes = maximumRecordBytes;
        Files.createDirectories(captureDirectory);
        Files.createDirectories(claimDirectory);
        Files.createDirectories(corruptDirectory);
    }

    public synchronized void writeCapture(CaptureJournalRecord record) throws IOException {
        Properties properties = common(record.operationToken(), record.state().name());
        Grave grave = record.grave();
        properties.setProperty("graveId", grave.graveId().toString());
        properties.setProperty("shortId", grave.shortId());
        properties.setProperty("ownerUuid", grave.ownerUuid().toString());
        properties.setProperty("ownerName", grave.ownerName());
        properties.setProperty("serverId", grave.serverId());
        properties.setProperty("inventoryScope", grave.inventoryScope());
        writeLocation(properties, "death", grave.deathLocation());
        writeLocation(properties, "grave", grave.location());
        properties.setProperty("placementType", grave.placementType().name());
        properties.setProperty("graveStatus", grave.status().name());
        properties.setProperty("createdWallMillis", Long.toString(grave.createdWallMillis()));
        properties.setProperty("createdActiveMillis", Long.toString(grave.createdActiveMillis()));
        properties.setProperty("expiresActiveMillis", Long.toString(grave.expiresActiveMillis()));
        if (grave.pausedRemainingMillis() != null) {
            properties.setProperty("pausedRemainingMillis", grave.pausedRemainingMillis().toString());
        }
        properties.setProperty("itemEntryCount", Integer.toString(grave.itemEntryCount()));
        properties.setProperty("remainingExperience", Integer.toString(grave.remainingExperience()));
        properties.setProperty("payloadRevision", Long.toString(grave.payloadRevision()));
        properties.setProperty("payloadChecksum", grave.payloadChecksum());
        properties.setProperty("ownerWasVanished", Boolean.toString(grave.ownerWasVanished()));
        if (grave.deathCause() != null) {
            properties.setProperty("deathCause", grave.deathCause());
        }
        properties.setProperty("payload", Base64.getEncoder().encodeToString(record.payload().bytes()));
        properties.setProperty("encodedChecksum", record.payload().checksum());
        writeAtomic(capturePath(grave.graveId()), properties);
    }

    public synchronized void writeClaim(ClaimJournalRecord record) throws IOException {
        Properties properties = common(record.operationToken(), record.state().name());
        properties.setProperty("graveId", record.graveId().toString());
        properties.setProperty("ownerUuid", record.ownerUuid().toString());
        properties.setProperty("actorUuid", record.actorUuid().toString());
        properties.setProperty("previousRevision", Long.toString(record.previousRevision()));
        properties.setProperty("transferredEntries", Integer.toString(record.transferredEntries()));
        properties.setProperty("transferredExperience", Integer.toString(record.transferredExperience()));
        properties.setProperty("payload", Base64.getEncoder().encodeToString(record.remainingPayload().bytes()));
        properties.setProperty("encodedChecksum", record.remainingPayload().checksum());
        writeAtomic(claimPath(record.operationToken()), properties);
    }

    public synchronized List<CaptureJournalRecord> loadCaptures() throws IOException {
        List<CaptureJournalRecord> records = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(captureDirectory, "*" + SUFFIX)) {
            for (Path path : stream) {
                try {
                    records.add(readCapture(path));
                } catch (RuntimeException | IOException exception) {
                    quarantine(path);
                }
            }
        }
        return List.copyOf(records);
    }

    public synchronized List<ClaimJournalRecord> loadClaims() throws IOException {
        List<ClaimJournalRecord> records = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(claimDirectory, "*" + SUFFIX)) {
            for (Path path : stream) {
                try {
                    records.add(readClaim(path));
                } catch (RuntimeException | IOException exception) {
                    quarantine(path);
                }
            }
        }
        return List.copyOf(records);
    }

    public synchronized void deleteCapture(UUID graveId) throws IOException {
        if (Files.deleteIfExists(capturePath(graveId))) {
            forceDirectory(captureDirectory);
        }
    }

    public synchronized void deleteClaim(UUID operationToken) throws IOException {
        if (Files.deleteIfExists(claimPath(operationToken))) {
            forceDirectory(claimDirectory);
        }
    }

    /**
     * Returns operation tokens whose claim journals were quarantined. These tokens must remain
     * reserved in the database: clearing one without knowing whether playerdata was applied could
     * make the same payload claimable again.
     */
    public synchronized Set<UUID> quarantinedClaimTokens() throws IOException {
        Set<UUID> tokens = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(corruptDirectory)) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                int suffix = name.indexOf(SUFFIX);
                if (suffix <= 0) {
                    continue;
                }
                try {
                    tokens.add(UUID.fromString(name.substring(0, suffix)));
                } catch (IllegalArgumentException ignored) {
                    // Capture journals use grave ids; preserving those as operation tokens is safe
                    // but unnecessary, so malformed or non-token filenames are ignored.
                }
            }
        }
        return Set.copyOf(tokens);
    }

    private CaptureJournalRecord readCapture(Path path) throws IOException {
        Properties properties = read(path);
        try {
            UUID operationToken = UUID.fromString(required(properties, "operationToken"));
            CaptureJournalState state = CaptureJournalState.valueOf(required(properties, "state"));
            GraveLocation death = readLocation(properties, "death");
            GraveLocation location = readLocation(properties, "grave");
            Long paused = properties.containsKey("pausedRemainingMillis")
                    ? Long.parseLong(properties.getProperty("pausedRemainingMillis"))
                    : null;
            Grave grave = new Grave(
                    UUID.fromString(required(properties, "graveId")),
                    required(properties, "shortId"),
                    UUID.fromString(required(properties, "ownerUuid")),
                    required(properties, "ownerName"),
                    required(properties, "serverId"),
                    required(properties, "inventoryScope"),
                    death,
                    location,
                    GravePlacementType.valueOf(required(properties, "placementType")),
                    GraveStatus.valueOf(required(properties, "graveStatus")),
                    Long.parseLong(required(properties, "createdWallMillis")),
                    Long.parseLong(required(properties, "createdActiveMillis")),
                    Long.parseLong(required(properties, "expiresActiveMillis")),
                    paused,
                    Integer.parseInt(required(properties, "itemEntryCount")),
                    Integer.parseInt(required(properties, "remainingExperience")),
                    Long.parseLong(required(properties, "payloadRevision")),
                    required(properties, "payloadChecksum"),
                    properties.getProperty("deathCause"),
                    Boolean.parseBoolean(required(properties, "ownerWasVanished"))
            );
            EncodedGravePayload payload = encodedPayload(properties);
            return new CaptureJournalRecord(operationToken, state, grave, payload);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid Graveyard capture journal record: " + path, exception);
        }
    }

    private ClaimJournalRecord readClaim(Path path) throws IOException {
        Properties properties = read(path);
        try {
            return new ClaimJournalRecord(
                    UUID.fromString(required(properties, "operationToken")),
                    ClaimJournalState.valueOf(required(properties, "state")),
                    UUID.fromString(required(properties, "graveId")),
                    UUID.fromString(required(properties, "ownerUuid")),
                    UUID.fromString(required(properties, "actorUuid")),
                    Long.parseLong(required(properties, "previousRevision")),
                    Integer.parseInt(required(properties, "transferredEntries")),
                    Integer.parseInt(required(properties, "transferredExperience")),
                    encodedPayload(properties)
            );
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid Graveyard claim journal record: " + path, exception);
        }
    }

    private Properties read(Path path) throws IOException {
        long size = Files.size(path);
        if (size <= 0L || size > maximumRecordBytes) {
            throw new IOException("Invalid Graveyard journal size " + size);
        }
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(Files.readAllBytes(path)));
        return properties;
    }

    private void writeAtomic(Path target, Properties properties) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        properties.store(bytes, "ServerFeatures Graveyard operation journal");
        if (bytes.size() > maximumRecordBytes) {
            throw new IOException("Graveyard journal record exceeds configured limit: " + bytes.size());
        }
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(
                temporary,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        )) {
            channel.write(ByteBuffer.wrap(bytes.toByteArray()));
            channel.force(true);
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        forceDirectory(target.getParent());
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException ignored) {
            // Some filesystems do not expose directory fsync through FileChannel.
        }
    }

    private void quarantine(Path path) throws IOException {
        Path target = corruptDirectory.resolve(path.getFileName() + "." + System.currentTimeMillis());
        Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
        forceDirectory(path.getParent());
        forceDirectory(corruptDirectory);
    }

    private Path capturePath(UUID graveId) {
        return captureDirectory.resolve(graveId + SUFFIX);
    }

    private Path claimPath(UUID operationToken) {
        return claimDirectory.resolve(operationToken + SUFFIX);
    }

    private static Properties common(UUID operationToken, String state) {
        Properties properties = new Properties();
        properties.setProperty("operationToken", operationToken.toString());
        properties.setProperty("state", state);
        return properties;
    }

    private static void writeLocation(Properties properties, String prefix, GraveLocation location) {
        properties.setProperty(prefix + "WorldUuid", location.worldUuid().toString());
        properties.setProperty(prefix + "WorldKey", location.worldKey());
        properties.setProperty(prefix + "X", Double.toString(location.x()));
        properties.setProperty(prefix + "Y", Double.toString(location.y()));
        properties.setProperty(prefix + "Z", Double.toString(location.z()));
        properties.setProperty(prefix + "Yaw", Float.toString(location.yaw()));
    }

    private static GraveLocation readLocation(Properties properties, String prefix) {
        try {
            return new GraveLocation(
                    UUID.fromString(required(properties, prefix + "WorldUuid")),
                    required(properties, prefix + "WorldKey"),
                    Double.parseDouble(required(properties, prefix + "X")),
                    Double.parseDouble(required(properties, prefix + "Y")),
                    Double.parseDouble(required(properties, prefix + "Z")),
                    Float.parseFloat(required(properties, prefix + "Yaw"))
            );
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Invalid numeric Graveyard journal location for prefix " + prefix,
                    exception
            );
        }
    }

    private static EncodedGravePayload encodedPayload(Properties properties) {
        return new EncodedGravePayload(
                Base64.getDecoder().decode(required(properties, "payload")),
                required(properties, "encodedChecksum")
        );
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing Graveyard journal property " + key);
        }
        return value;
    }
}
