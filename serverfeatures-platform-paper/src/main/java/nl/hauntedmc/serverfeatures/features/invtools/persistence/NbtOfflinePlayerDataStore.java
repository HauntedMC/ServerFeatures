package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.NBTType;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBTCompoundList;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBTList;
import de.tr7zw.changeme.nbtapi.utils.DataFixerUtil;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * Reads Paper's player record and mutates only Inventory, EnderItems, and the five player equipment
 * entries owned by InvTools. Older records are upgraded with Paper's full PLAYER data fixer before
 * they are exposed to the current-schema inventory decoder.
 */
public final class NbtOfflinePlayerDataStore implements OfflinePlayerDataStore {

    private static final int MAX_COMPRESSED_PLAYER_DATA_BYTES = 4 * 1024 * 1024;
    private static final int MAX_DECOMPRESSED_PLAYER_DATA_BYTES = 32 * 1024 * 1024;
    private static final int PLAYER_LOCK_COUNT = 64;
    private static final String MIGRATION_BACKUP_SUFFIX = ".invtools-migration-backup";

    private final Path playerDataDirectory;
    private final int runtimeDataVersion;
    private final Object[] playerLocks = createPlayerLocks();
    private final PlayerDataIdentityIndex identityIndex;
    private final PlayerDataConverter playerDataConverter;
    private final PlayerDataMigrationObserver migrationObserver;
    private final PlayerDataNbtIo nbtIo;
    private final PlayerDataMigrationCheckpoint migrationCheckpoint;

    public NbtOfflinePlayerDataStore(Path levelDirectory) {
        this(
                levelDirectory,
                DataFixerUtil.getCurrentVersion(),
                null,
                new PaperPlayerDataConverter(),
                PlayerDataMigrationObserver.NONE,
                new NbtApiPlayerDataIo(),
                PlayerDataMigrationCheckpoint.NONE
        );
    }

    NbtOfflinePlayerDataStore(Path levelDirectory, int runtimeDataVersion) {
        this(
                levelDirectory,
                runtimeDataVersion,
                null,
                new PaperPlayerDataConverter(),
                PlayerDataMigrationObserver.NONE,
                new NbtApiPlayerDataIo(),
                PlayerDataMigrationCheckpoint.NONE
        );
    }

    public NbtOfflinePlayerDataStore(Path levelDirectory, Path pluginDataDirectory) {
        this(
                levelDirectory,
                DataFixerUtil.getCurrentVersion(),
                pluginDataDirectory,
                new PaperPlayerDataConverter(),
                PlayerDataMigrationObserver.NONE,
                new NbtApiPlayerDataIo(),
                PlayerDataMigrationCheckpoint.NONE
        );
    }

    public NbtOfflinePlayerDataStore(
            Path levelDirectory,
            Path pluginDataDirectory,
            PlayerDataMigrationObserver migrationObserver
    ) {
        this(
                levelDirectory,
                DataFixerUtil.getCurrentVersion(),
                pluginDataDirectory,
                new PaperPlayerDataConverter(),
                migrationObserver,
                new NbtApiPlayerDataIo(),
                PlayerDataMigrationCheckpoint.NONE
        );
    }

    NbtOfflinePlayerDataStore(
            Path levelDirectory,
            int runtimeDataVersion,
            PlayerDataConverter playerDataConverter,
            PlayerDataMigrationObserver migrationObserver,
            PlayerDataMigrationCheckpoint migrationCheckpoint
    ) {
        this(
                levelDirectory,
                runtimeDataVersion,
                null,
                playerDataConverter,
                migrationObserver,
                new NbtApiPlayerDataIo(),
                migrationCheckpoint
        );
    }

    NbtOfflinePlayerDataStore(
            Path levelDirectory,
            int runtimeDataVersion,
            PlayerDataConverter playerDataConverter,
            PlayerDataMigrationObserver migrationObserver,
            PlayerDataNbtIo nbtIo,
            PlayerDataMigrationCheckpoint migrationCheckpoint
    ) {
        this(
                levelDirectory,
                runtimeDataVersion,
                null,
                playerDataConverter,
                migrationObserver,
                nbtIo,
                migrationCheckpoint
        );
    }

    private NbtOfflinePlayerDataStore(
            Path levelDirectory,
            int runtimeDataVersion,
            Path pluginDataDirectory,
            PlayerDataConverter playerDataConverter,
            PlayerDataMigrationObserver migrationObserver,
            PlayerDataNbtIo nbtIo,
            PlayerDataMigrationCheckpoint migrationCheckpoint
    ) {
        Path normalizedLevelDirectory = Objects.requireNonNull(levelDirectory, "levelDirectory")
                .toAbsolutePath()
                .normalize();
        this.playerDataDirectory = PaperPlayerDataLayout.playerDataDirectory(
                normalizedLevelDirectory
        );
        if (runtimeDataVersion <= 0) {
            throw new IllegalArgumentException("runtimeDataVersion must be positive");
        }
        this.runtimeDataVersion = runtimeDataVersion;
        this.playerDataConverter = Objects.requireNonNull(
                playerDataConverter,
                "playerDataConverter"
        );
        this.migrationObserver = Objects.requireNonNull(
                migrationObserver,
                "migrationObserver"
        );
        this.nbtIo = Objects.requireNonNull(nbtIo, "nbtIo");
        this.migrationCheckpoint = Objects.requireNonNull(
                migrationCheckpoint,
                "migrationCheckpoint"
        );
        this.identityIndex = new PlayerDataIdentityIndex(
                playerDataDirectory,
                this::readLastKnownName,
                userCacheLocations(normalizedLevelDirectory, pluginDataDirectory)
        );
    }

    @Override
    public boolean hasPlayerData(UUID playerId) throws IOException {
        Path file = playerFile(playerId);
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(file);
    }

    @Override
    public OfflinePlayerData load(UUID playerId) throws IOException {
        Objects.requireNonNull(playerId, "playerId");
        migrationObserver.operationStarted(playerId);
        try {
            synchronized (playerLock(playerId)) {
                return loadLocked(playerId);
            }
        } catch (IOException | RuntimeException exception) {
            migrationObserver.loadFailed(playerId, exception);
            throw exception;
        } finally {
            migrationObserver.operationFinished(playerId);
        }
    }

    private OfflinePlayerData loadLocked(UUID playerId) throws IOException {
        Path file = playerFile(playerId);
        recoverInterruptedMigration(file, playerId);

        byte[] bytes = readPlayerData(file);
        ReadWriteNBT root = nbtIo.read(bytes, file);
        validatePlayerIdentity(root, playerId);
        int sourceVersion = dataVersion(root, playerId);
        if (sourceVersion > runtimeDataVersion) {
            migrationObserver.migrationNotRequired(playerId);
            throw new IOException(
                    "Playerdata version " + sourceVersion + " is newer than the running Paper "
                            + "version " + runtimeDataVersion + " for " + playerId
            );
        }
        InventorySnapshot snapshot;
        if (sourceVersion < runtimeDataVersion) {
            MigratedPlayerData migrated = migratePlayerData(
                    file,
                    playerId,
                    bytes,
                    root,
                    sourceVersion
            );
            bytes = migrated.bytes();
            root = migrated.root();
            snapshot = decodeSnapshot(root, false, new ArrayList<>());
        } else {
            migrationObserver.migrationNotRequired(playerId);
            List<ItemComponentRepair> repairs = new ArrayList<>();
            snapshot = decodeSnapshot(root, true, repairs);
            if (!repairs.isEmpty()) {
                PlayerDataRevision originalRevision = revision(bytes);
                bytes = writeAtomically(
                        file,
                        root,
                        originalRevision,
                        playerId,
                        (temporaryBytes, temporaryFile) -> {
                            ReadWriteNBT verifiedRoot = nbtIo.read(
                                    temporaryBytes,
                                    temporaryFile
                            );
                            validateCurrentPlayerData(
                                    verifiedRoot,
                                    playerId,
                                    false,
                                    new ArrayList<>()
                            );
                        }
                );

                root = nbtIo.read(bytes, file);
                validatePlayerIdentity(root, playerId);
                if (dataVersion(root, playerId) != runtimeDataVersion) {
                    throw new IOException(
                            "Repaired playerdata version changed for " + playerId
                    );
                }
                snapshot = decodeSnapshot(root, false, new ArrayList<>());
                notifyRepairs(playerId, repairs);
            }
        }

        return new OfflinePlayerData(
                playerId,
                snapshot,
                revision(bytes)
        );
    }

    @Override
    public Optional<UUID> resolvePlayerId(
            Optional<UUID> preferredPlayerId,
            String playerName
    ) throws IOException {
        Optional<UUID> result;
        try {
            result = identityIndex.resolve(preferredPlayerId, playerName);
        } catch (IOException | RuntimeException exception) {
            migrationObserver.identityResolved(playerName, Optional.empty());
            throw exception;
        }
        migrationObserver.identityResolved(playerName, result);
        return result;
    }

    @Override
    public void rememberPlayerIdentity(UUID playerId, String playerName) {
        identityIndex.remember(playerId, playerName);
    }

    @Override
    public void save(
            OfflinePlayerData original,
            InventoryKind kind,
            InventorySnapshot changedSnapshot
    ) throws IOException {
        UUID playerId = original.playerId();
        migrationObserver.operationStarted(playerId);
        try {
            synchronized (playerLock(playerId)) {
                Path file = playerFile(playerId);
                byte[] currentBytes = readPlayerData(file);
                requireRevision(original.revision(), currentBytes, playerId);

                ReadWriteNBT root = nbtIo.read(currentBytes, file);
                validatePlayerIdentity(root, playerId);
                if (dataVersion(root, playerId) != runtimeDataVersion) {
                    throw new PlayerDataConflictException(
                            "Playerdata version changed after InvTools opened it for " + playerId
                    );
                }
                if (kind == InventoryKind.PLAYER) {
                    writeInventory(root, original.snapshot(), changedSnapshot);
                } else {
                    writeEnderChest(root, original.snapshot(), changedSnapshot);
                }
                writeAtomically(file, root, original.revision(), playerId);
            }
        } finally {
            migrationObserver.operationFinished(playerId);
        }
    }

    private MigratedPlayerData migratePlayerData(
            Path file,
            UUID playerId,
            byte[] originalBytes,
            ReadWriteNBT originalRoot,
            int sourceVersion
    ) throws IOException {
        Path backup = migrationBackup(file);
        PlayerDataRevision originalRevision = revision(originalBytes);
        migrationObserver.migrationDetected(
                playerId,
                sourceVersion,
                runtimeDataVersion,
                backup
        );

        boolean targetReplaced = false;
        PlayerDataRevision installedRevision = null;
        Path temporary = null;
        PlayerDataMigrationException migrationFailure = null;
        try {
            writeMigrationBackup(file, backup, originalBytes, originalRevision, playerId);
            migrationObserver.backupCreated(
                    playerId,
                    sourceVersion,
                    runtimeDataVersion,
                    backup
            );
            migrationCheckpoint.reached(
                    playerId,
                    PlayerDataMigrationCheckpoint.Stage.AFTER_BACKUP
            );

            migrationObserver.conversionStarted(playerId, sourceVersion, runtimeDataVersion);
            ReadWriteNBT converted = playerDataConverter.convertToCurrent(
                    originalRoot,
                    sourceVersion,
                    runtimeDataVersion
            );
            List<ItemComponentRepair> repairs = new ArrayList<>();
            validateCurrentPlayerData(converted, playerId, true, repairs);

            temporary = Files.createTempFile(
                    file.getParent(),
                    playerId + ".invtools-migration-",
                    ".dat"
            );
            copyPermissions(file, temporary);
            nbtIo.write(temporary, converted);
            forceFile(temporary);

            byte[] convertedBytes = readPlayerData(temporary);
            ReadWriteNBT verifiedConverted = nbtIo.read(convertedBytes, temporary);
            validateCurrentPlayerData(
                    verifiedConverted,
                    playerId,
                    false,
                    new ArrayList<>()
            );
            installedRevision = revision(convertedBytes);

            migrationCheckpoint.reached(
                    playerId,
                    PlayerDataMigrationCheckpoint.Stage.BEFORE_REPLACE
            );
            requireRevision(originalRevision, readPlayerData(file), playerId);
            moveAtomically(temporary, file);
            temporary = null;
            targetReplaced = true;
            forceDirectory(file.getParent());

            migrationCheckpoint.reached(
                    playerId,
                    PlayerDataMigrationCheckpoint.Stage.AFTER_REPLACE
            );
            byte[] committedBytes = readPlayerData(file);
            if (!installedRevision.equals(revision(committedBytes))) {
                throw new IOException(
                        "Migrated playerdata did not match the validated temporary file for "
                                + playerId
                );
            }
            ReadWriteNBT committedRoot = nbtIo.read(committedBytes, file);
            validateCurrentPlayerData(committedRoot, playerId, false, new ArrayList<>());

            deleteMigrationBackupAfterSuccess(
                    backup,
                    playerId,
                    sourceVersion,
                    runtimeDataVersion
            );
            notifyRepairs(playerId, repairs);
            migrationObserver.migrationCompleted(
                    playerId,
                    sourceVersion,
                    runtimeDataVersion
            );
            return new MigratedPlayerData(committedBytes, committedRoot);
        } catch (IOException | RuntimeException exception) {
            migrationFailure = recoverFailedMigration(
                    file,
                    backup,
                    playerId,
                    originalBytes,
                    originalRevision,
                    sourceVersion,
                    targetReplaced,
                    installedRevision,
                    exception
            );
            throw migrationFailure;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException | RuntimeException cleanupFailure) {
                    if (migrationFailure != null) {
                        migrationFailure.addSuppressed(cleanupFailure);
                    } else {
                        throw cleanupFailure;
                    }
                }
            }
        }
    }

    private PlayerDataMigrationException recoverFailedMigration(
            Path file,
            Path backup,
            UUID playerId,
            byte[] originalBytes,
            PlayerDataRevision originalRevision,
            int sourceVersion,
            boolean targetReplaced,
            PlayerDataRevision installedRevision,
            Throwable failure
    ) {
        PlayerDataMigrationException.RecoveryStatus recoveryStatus;
        Throwable recoveryFailure = null;
        try {
            if (targetReplaced && canRestoreInstalledTarget(
                    file,
                    installedRevision
            )) {
                migrationObserver.rollbackStarted(
                        playerId,
                        sourceVersion,
                        runtimeDataVersion,
                        backup
                );
                restoreMigrationBackup(file, backup, originalRevision, playerId);
                recoveryStatus = PlayerDataMigrationException.RecoveryStatus.RESTORED_FROM_BACKUP;
            } else if (targetReplaced) {
                recoveryStatus = PlayerDataMigrationException.RecoveryStatus.BACKUP_RETAINED;
            } else if (targetStillMatches(file, originalBytes, originalRevision)) {
                deleteRegularBackup(backup);
                forceDirectory(file.getParent());
                recoveryStatus = PlayerDataMigrationException.RecoveryStatus.ORIGINAL_UNCHANGED;
            } else {
                recoveryStatus = PlayerDataMigrationException.RecoveryStatus.BACKUP_RETAINED;
            }
        } catch (IOException | RuntimeException exception) {
            recoveryFailure = exception;
            recoveryStatus = PlayerDataMigrationException.RecoveryStatus.BACKUP_RETAINED;
        }

        PlayerDataMigrationException result = new PlayerDataMigrationException(
                "Could not safely migrate playerdata from " + sourceVersion + " to "
                        + runtimeDataVersion + " for " + playerId + "; recovery="
                        + recoveryStatus,
                playerId,
                sourceVersion,
                runtimeDataVersion,
                recoveryStatus,
                backup,
                failure
        );
        if (recoveryFailure != null) {
            result.addSuppressed(recoveryFailure);
        }
        migrationObserver.migrationFailed(
                playerId,
                sourceVersion,
                runtimeDataVersion,
                recoveryStatus,
                backup,
                result
        );
        return result;
    }

    private boolean canRestoreInstalledTarget(
            Path file,
            PlayerDataRevision installedRevision
    ) throws IOException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return true;
        }
        if (Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }

        byte[] currentBytes;
        try {
            currentBytes = readPlayerData(file);
        } catch (IOException exception) {
            return true;
        }
        if (installedRevision != null && installedRevision.equals(revision(currentBytes))) {
            return true;
        }

        return !isParseableNbt(currentBytes, file);
    }

    private void recoverInterruptedMigration(Path file, UUID playerId) throws IOException {
        Path backup = migrationBackup(file);
        if (!Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        requireRegularFile(backup, "migration backup");

        byte[] backupBytes = readPlayerData(backup);
        ReadWriteNBT backupRoot = nbtIo.read(backupBytes, backup);
        validatePlayerIdentity(backupRoot, playerId);
        int backupVersion = dataVersion(backupRoot, playerId);
        PlayerDataRevision backupRevision = revision(backupBytes);

        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            migrationObserver.rollbackStarted(
                    playerId,
                    backupVersion,
                    runtimeDataVersion,
                    backup
            );
            restoreMigrationBackup(file, backup, backupRevision, playerId);
            return;
        }
        requireRegularFile(file, "playerdata");

        byte[] targetBytes;
        try {
            targetBytes = readPlayerData(file);
        } catch (IOException exception) {
            migrationObserver.rollbackStarted(
                    playerId,
                    backupVersion,
                    runtimeDataVersion,
                    backup
            );
            restoreMigrationBackup(file, backup, backupRevision, playerId);
            return;
        }
        if (revision(targetBytes).equals(backupRevision)) {
            deleteRegularBackup(backup);
            forceDirectory(file.getParent());
            return;
        }

        try {
            ReadWriteNBT targetRoot = nbtIo.read(targetBytes, file);
            validateCurrentPlayerData(targetRoot, playerId, false, new ArrayList<>());
            deleteRegularBackup(backup);
            forceDirectory(file.getParent());
        } catch (IOException | RuntimeException invalidTarget) {
            if (isParseableNbt(targetBytes, file)) {
                PlayerDataMigrationException exception = new PlayerDataMigrationException(
                        "Found an ambiguous interrupted migration for " + playerId
                                + "; both the playerdata and backup are retained",
                        playerId,
                        backupVersion,
                        runtimeDataVersion,
                        PlayerDataMigrationException.RecoveryStatus.BACKUP_RETAINED,
                        backup,
                        invalidTarget
                );
                migrationObserver.migrationFailed(
                        playerId,
                        backupVersion,
                        runtimeDataVersion,
                        PlayerDataMigrationException.RecoveryStatus.BACKUP_RETAINED,
                        backup,
                        exception
                );
                throw exception;
            }
            migrationObserver.rollbackStarted(
                    playerId,
                    backupVersion,
                    runtimeDataVersion,
                    backup
            );
            restoreMigrationBackup(file, backup, backupRevision, playerId);
        }
    }

    private boolean isParseableNbt(byte[] bytes, Path file) {
        try {
            nbtIo.read(bytes, file);
            return true;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private void writeMigrationBackup(
            Path target,
            Path backup,
            byte[] originalBytes,
            PlayerDataRevision originalRevision,
            UUID playerId
    ) throws IOException {
        if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(
                    "A migration backup already exists for " + playerId + ": " + backup
            );
        }
        Path temporary = Files.createTempFile(
                target.getParent(),
                playerId + ".invtools-migration-backup-",
                ".dat"
        );
        try {
            copyPermissions(target, temporary);
            Files.write(temporary, originalBytes, StandardOpenOption.TRUNCATE_EXISTING);
            forceFile(temporary);
            if (!originalRevision.equals(revision(readPlayerData(temporary)))) {
                throw new IOException("Migration backup verification failed for " + playerId);
            }
            requireRevision(originalRevision, readPlayerData(target), playerId);
            moveAtomically(temporary, backup);
            forceDirectory(target.getParent());
            requireRegularFile(backup, "migration backup");
            if (!originalRevision.equals(revision(readPlayerData(backup)))) {
                throw new IOException(
                        "Persisted migration backup verification failed for " + playerId
                );
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void restoreMigrationBackup(
            Path target,
            Path backup,
            PlayerDataRevision backupRevision,
            UUID playerId
    ) throws IOException {
        requireRegularFile(backup, "migration backup");
        byte[] backupBytes = readPlayerData(backup);
        if (!backupRevision.equals(revision(backupBytes))) {
            throw new IOException("Migration backup changed before restore for " + playerId);
        }

        Path temporary = Files.createTempFile(
                target.getParent(),
                playerId + ".invtools-migration-restore-",
                ".dat"
        );
        try {
            Path permissionSource = Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(target)
                    ? target
                    : backup;
            copyPermissions(permissionSource, temporary);
            Files.write(temporary, backupBytes, StandardOpenOption.TRUNCATE_EXISTING);
            forceFile(temporary);
            moveAtomically(temporary, target);
            forceDirectory(target.getParent());
            if (!backupRevision.equals(revision(readPlayerData(target)))) {
                throw new IOException("Restored playerdata verification failed for " + playerId);
            }
            deleteRegularBackup(backup);
            forceDirectory(target.getParent());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void deleteMigrationBackupAfterSuccess(
            Path backup,
            UUID playerId,
            int sourceVersion,
            int targetVersion
    ) {
        try {
            deleteRegularBackup(backup);
            forceDirectory(backup.getParent());
        } catch (IOException | RuntimeException exception) {
            migrationObserver.backupCleanupFailed(
                    playerId,
                    sourceVersion,
                    targetVersion,
                    backup,
                    exception
            );
        }
    }

    private static boolean targetStillMatches(
            Path target,
            byte[] originalBytes,
            PlayerDataRevision originalRevision
    ) throws IOException {
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(target)) {
            return false;
        }
        byte[] currentBytes = readPlayerData(target);
        return originalRevision.equals(revision(currentBytes))
                && MessageDigest.isEqual(originalBytes, currentBytes);
    }

    private static void deleteRegularBackup(Path backup) throws IOException {
        if (!Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        requireRegularFile(backup, "migration backup");
        Files.delete(backup);
    }

    private static void requireRegularFile(Path file, String description) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(file)) {
            throw new IOException(description + " is not a regular file: " + file);
        }
    }

    private Path migrationBackup(Path file) throws IOException {
        Path backup = file.resolveSibling(file.getFileName() + MIGRATION_BACKUP_SUFFIX)
                .normalize();
        if (!backup.getParent().equals(playerDataDirectory)) {
            throw new IOException("Resolved migration backup escaped the playerdata directory");
        }
        return backup;
    }

    private int dataVersion(ReadableNBT root, UUID playerId) throws IOException {
        if (!root.hasTag("DataVersion", NBTType.NBTTagInt)) {
            throw new IOException("Playerdata is missing its integer DataVersion for " + playerId);
        }
        int version = root.getInteger("DataVersion");
        if (version <= 0) {
            throw new IOException("Playerdata has an invalid DataVersion for " + playerId);
        }
        return version;
    }

    private void validateCurrentPlayerData(
            ReadWriteNBT root,
            UUID playerId,
            boolean repairMalformedComponents,
            List<ItemComponentRepair> repairs
    ) throws IOException {
        validatePlayerIdentity(root, playerId);
        int version = dataVersion(root, playerId);
        if (version != runtimeDataVersion) {
            throw new IOException(
                    "Converted playerdata version " + version + " does not match Paper version "
                            + runtimeDataVersion + " for " + playerId
            );
        }
        decodeSnapshot(root, repairMalformedComponents, repairs);
    }

    private InventorySnapshot decodeSnapshot(
            ReadWriteNBT root,
            boolean repairMalformedComponents,
            List<ItemComponentRepair> repairs
    ) throws IOException {
        ItemStack[] storage = new ItemStack[InventorySnapshot.STORAGE_SIZE];
        ItemStack[] enderChest = new ItemStack[InventorySnapshot.ENDER_CHEST_SIZE];
        ItemStack[] equipment = new ItemStack[5];

        readInventory(root, storage, equipment, repairMalformedComponents, repairs);
        readEnderChest(root, enderChest, repairMalformedComponents, repairs);
        return new InventorySnapshot(
                storage,
                equipment[0],
                equipment[1],
                equipment[2],
                equipment[3],
                equipment[4],
                enderChest
        );
    }

    private void readInventory(
            ReadWriteNBT root,
            ItemStack[] storage,
            ItemStack[] equipment,
            boolean repairMalformedComponents,
            List<ItemComponentRepair> repairs
    ) throws IOException {
        validateSlottedList(root, "Inventory", "Inventory", InventorySnapshot.STORAGE_SIZE);
        for (ReadWriteNBT entry : root.getCompoundList("Inventory")) {
            int slot = slot(entry, "Inventory");
            storage[slot] = decodeItem(
                    entry,
                    "Inventory slot " + slot,
                    repairMalformedComponents,
                    repairs
            );
        }

        validateCurrentEquipment(root);
        ReadWriteNBT equipmentData = root.getCompound("equipment");
        if (equipmentData != null) {
            equipment[0] = decodeOptionalItem(equipmentData.getCompound("head"), "helmet",
                    repairMalformedComponents, repairs);
            equipment[1] = decodeOptionalItem(equipmentData.getCompound("chest"), "chestplate",
                    repairMalformedComponents, repairs);
            equipment[2] = decodeOptionalItem(equipmentData.getCompound("legs"), "leggings",
                    repairMalformedComponents, repairs);
            equipment[3] = decodeOptionalItem(equipmentData.getCompound("feet"), "boots",
                    repairMalformedComponents, repairs);
            equipment[4] = decodeOptionalItem(equipmentData.getCompound("offhand"), "offhand",
                    repairMalformedComponents, repairs);
        }
    }

    private void readEnderChest(
            ReadWriteNBT root,
            ItemStack[] enderChest,
            boolean repairMalformedComponents,
            List<ItemComponentRepair> repairs
    ) throws IOException {
        validateSlottedList(
                root,
                "EnderItems",
                "EnderItems",
                InventorySnapshot.ENDER_CHEST_SIZE
        );
        for (ReadWriteNBT entry : root.getCompoundList("EnderItems")) {
            int slot = slot(entry, "EnderItems");
            enderChest[slot] = decodeItem(
                    entry,
                    "EnderItems slot " + slot,
                    repairMalformedComponents,
                    repairs
            );
        }
    }

    private void writeInventory(
            ReadWriteNBT root,
            InventorySnapshot original,
            InventorySnapshot changed
    ) {
        ReadWriteNBTCompoundList inventory = root.getCompoundList("Inventory");
        for (int slot = 0; slot < InventorySnapshot.STORAGE_SIZE; slot++) {
            patchSlottedItem(
                    inventory,
                    slot,
                    original.itemAt(InventoryKind.PLAYER, slot),
                    changed.itemAt(InventoryKind.PLAYER, slot)
            );
        }

        ReadWriteNBT equipment = root.getOrCreateCompound("equipment");
        patchEquipmentItem(equipment, "head", original.helmet(), changed.helmet());
        patchEquipmentItem(equipment, "chest", original.chestplate(), changed.chestplate());
        patchEquipmentItem(equipment, "legs", original.leggings(), changed.leggings());
        patchEquipmentItem(equipment, "feet", original.boots(), changed.boots());
        patchEquipmentItem(equipment, "offhand", original.offHand(), changed.offHand());
    }

    private void writeEnderChest(
            ReadWriteNBT root,
            InventorySnapshot original,
            InventorySnapshot changed
    ) {
        ReadWriteNBTCompoundList enderItems = root.getCompoundList("EnderItems");
        for (int slot = 0; slot < InventorySnapshot.ENDER_CHEST_SIZE; slot++) {
            patchSlottedItem(
                    enderItems,
                    slot,
                    original.itemAt(InventoryKind.ENDER_CHEST, slot),
                    changed.itemAt(InventoryKind.ENDER_CHEST, slot)
            );
        }
    }

    private static void patchSlottedItem(
            ReadWriteNBTCompoundList destination,
            int slot,
            ItemStack original,
            ItemStack changed
    ) {
        if (sameItem(original, changed)) {
            return;
        }
        destination.removeIf(entry -> entry.hasTag("Slot", NBTType.NBTTagByte)
                && entry.getByte("Slot") == (byte) slot);
        addSlottedItem(destination, slot, changed);
    }

    private static void addSlottedItem(
            ReadWriteNBTCompoundList destination,
            int slot,
            ItemStack item
    ) {
        if (isEmpty(item)) {
            return;
        }
        ReadWriteNBT encoded = NBT.itemStackToNBT(item);
        encoded.setByte("Slot", (byte) slot);
        destination.addCompound(encoded);
    }

    private static void patchEquipmentItem(
            ReadWriteNBT equipment,
            String key,
            ItemStack original,
            ItemStack changed
    ) {
        if (sameItem(original, changed)) {
            return;
        }
        setEquipmentItem(equipment, key, changed);
    }

    private static void setEquipmentItem(ReadWriteNBT equipment, String key, ItemStack item) {
        if (isEmpty(item)) {
            equipment.removeKey(key);
            return;
        }
        ReadWriteNBT target = equipment.getOrCreateCompound(key);
        target.clearNBT();
        target.mergeCompound(NBT.itemStackToNBT(item));
    }

    private static ItemStack decodeOptionalItem(
            ReadWriteNBT item,
            String location,
            boolean repairMalformedComponents,
            List<ItemComponentRepair> repairs
    ) throws IOException {
        return item == null || item.isEmpty()
                ? null
                : decodeItem(item, location, repairMalformedComponents, repairs);
    }

    private static ItemStack decodeItem(
            ReadWriteNBT item,
            String location,
            boolean repairMalformedComponents,
            List<ItemComponentRepair> repairs
    ) throws IOException {
        MalformedItemComponentRepair.Result result = MalformedItemComponentRepair.decode(
                item,
                location,
                repairMalformedComponents
        );
        if (!result.removedComponents().isEmpty()) {
            repairs.add(new ItemComponentRepair(location, result.removedComponents()));
        }
        return result.item();
    }

    private void notifyRepairs(UUID playerId, List<ItemComponentRepair> repairs) {
        for (ItemComponentRepair repair : repairs) {
            migrationObserver.malformedItemComponentsRemoved(
                    playerId,
                    repair.location(),
                    repair.componentKeys()
            );
        }
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private record ItemComponentRepair(String location, List<String> componentKeys) {
    }

    private static void validateSlottedList(
            ReadableNBT root,
            String key,
            String location,
            int slotCount
    ) throws IOException {
        if (!root.hasTag(key, NBTType.NBTTagList)) {
            throw new IOException("Playerdata " + location + " is not a compound list");
        }
        ReadableNBTList<ReadWriteNBT> entries = root.getCompoundList(key);
        NBTType listType = root.getListType(key);
        if (listType != NBTType.NBTTagCompound
                && !(entries.isEmpty() && listType == NBTType.NBTTagEnd)) {
            throw new IOException("Playerdata " + location + " is not a compound list");
        }
        Set<Integer> slots = new HashSet<>();
        for (ReadWriteNBT entry : entries) {
            int slot = slot(entry, location);
            if (slot < 0 || slot >= slotCount) {
                throw new IOException("Playerdata " + location + " has unsupported slot " + slot);
            }
            if (!slots.add(slot)) {
                throw new IOException(
                        "Playerdata " + location + " contains duplicate slot " + slot
                );
            }
        }
    }

    private static int slot(ReadableNBT entry, String location) throws IOException {
        if (!entry.hasTag("Slot", NBTType.NBTTagByte)) {
            throw new IOException(
                    "Playerdata " + location + " entry is missing its byte Slot tag"
            );
        }
        return entry.getByte("Slot");
    }

    private static void validateCurrentEquipment(ReadableNBT root) throws IOException {
        if (root.hasTag("equipment")
                && !root.hasTag("equipment", NBTType.NBTTagCompound)) {
            throw new IOException("Playerdata equipment is not a compound");
        }
        ReadableNBT equipment = root.getCompound("equipment");
        if (equipment == null) {
            return;
        }
        for (String key : Set.of("head", "chest", "legs", "feet", "offhand")) {
            if (equipment.hasTag(key)
                    && !equipment.hasTag(key, NBTType.NBTTagCompound)) {
                throw new IOException("Playerdata equipment." + key + " is not a compound");
            }
        }
    }

    private static void validatePlayerIdentity(ReadableNBT root, UUID expectedPlayerId)
            throws IOException {
        if (!root.hasTag("UUID")) {
            return;
        }
        if (!root.hasTag("UUID", NBTType.NBTTagIntArray)) {
            throw new IOException("Playerdata UUID is not an integer array");
        }
        UUID storedPlayerId;
        try {
            storedPlayerId = root.getUUID("UUID");
        } catch (RuntimeException exception) {
            throw new IOException("Playerdata UUID is malformed", exception);
        }
        if (!expectedPlayerId.equals(storedPlayerId)) {
            throw new IOException(
                    "Playerdata UUID does not match its filename for " + expectedPlayerId
            );
        }
    }

    private static boolean sameItem(ItemStack first, ItemStack second) {
        if (isEmpty(first) || isEmpty(second)) {
            return isEmpty(first) && isEmpty(second);
        }
        return first.getAmount() == second.getAmount() && first.isSimilar(second);
    }

    private Path playerFile(UUID playerId) throws IOException {
        if (playerId == null) {
            throw new IOException("Player UUID is missing");
        }
        Path file = playerDataDirectory.resolve(playerId + ".dat").normalize();
        if (!file.getParent().equals(playerDataDirectory)) {
            throw new IOException("Resolved playerdata path escaped its directory");
        }
        return file;
    }

    private static List<Path> userCacheLocations(Path levelDirectory, Path pluginDataDirectory) {
        Path worldContainer = levelDirectory.getParent();
        Path serverDirectory = pluginDataDirectory == null
                ? null
                : pluginDataDirectory.toAbsolutePath().normalize().getParent();
        serverDirectory = serverDirectory == null ? null : serverDirectory.getParent();

        if (worldContainer == null && serverDirectory == null) {
            return List.of();
        }
        if (worldContainer == null) {
            return List.of(serverDirectory.resolve("usercache.json"));
        }
        if (serverDirectory == null || worldContainer.equals(serverDirectory)) {
            return List.of(worldContainer.resolve("usercache.json"));
        }
        return List.of(
                worldContainer.resolve("usercache.json"),
                serverDirectory.resolve("usercache.json")
        );
    }

    private String readLastKnownName(Path file) throws IOException {
        ReadWriteNBT root = nbtIo.read(readPlayerData(file), file);
        if (!root.hasTag("bukkit", NBTType.NBTTagCompound)) {
            return null;
        }
        ReadableNBT bukkitData = root.getCompound("bukkit");
        return bukkitData != null
                && bukkitData.hasTag("lastKnownName", NBTType.NBTTagString)
                ? bukkitData.getString("lastKnownName")
                : null;
    }

    private static byte[] readPlayerData(Path file) throws IOException {
        requireRegularFile(file, "playerdata file");
        try (InputStream input = Files.newInputStream(
                file,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS
        )) {
            byte[] bytes = input.readNBytes(MAX_COMPRESSED_PLAYER_DATA_BYTES + 1);
            if (bytes.length > MAX_COMPRESSED_PLAYER_DATA_BYTES) {
                throw new IOException(
                        "Playerdata file exceeds the safe read limit: " + file.getFileName()
                );
            }
            validateDecompressedSize(bytes, file);
            return bytes;
        }
    }

    private static void validateDecompressedSize(byte[] bytes, Path file) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = gzip.read(buffer)) >= 0) {
                total = Math.addExact(total, count);
                if (total > MAX_DECOMPRESSED_PLAYER_DATA_BYTES) {
                    throw new IOException(
                            "Playerdata expands beyond the safe read limit: " + file.getFileName()
                    );
                }
            }
        } catch (ArithmeticException exception) {
            throw new IOException(
                    "Playerdata expands beyond the safe read limit: " + file.getFileName(),
                    exception
            );
        }
    }

    private static PlayerDataRevision revision(byte[] bytes) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return new PlayerDataRevision(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireRevision(
            PlayerDataRevision expected,
            byte[] currentBytes,
            UUID playerId
    ) throws IOException {
        if (!expected.equals(revision(currentBytes))) {
            throw new PlayerDataConflictException(
                    "Playerdata changed after InvTools opened it for " + playerId
            );
        }
    }

    private byte[] writeAtomically(
            Path target,
            ReadWriteNBT root,
            PlayerDataRevision expected,
            UUID playerId
    ) throws IOException {
        return writeAtomically(target, root, expected, playerId, null);
    }

    private byte[] writeAtomically(
            Path target,
            ReadWriteNBT root,
            PlayerDataRevision expected,
            UUID playerId,
            TemporaryPlayerDataVerifier verifier
    ) throws IOException {
        Path temporary = Files.createTempFile(
                target.getParent(),
                playerId + ".invtools-",
                ".dat"
        );
        try {
            copyPermissions(target, temporary);
            nbtIo.write(temporary, root);
            forceFile(temporary);
            byte[] temporaryBytes = readPlayerData(temporary);
            if (verifier != null) {
                verifier.verify(temporaryBytes, temporary);
            }
            PlayerDataRevision installedRevision = revision(temporaryBytes);

            byte[] currentBytes = readPlayerData(target);
            requireRevision(expected, currentBytes, playerId);
            writeRecoveryBackup(target, currentBytes, playerId);
            requireRevision(expected, readPlayerData(target), playerId);
            moveAtomically(temporary, target);
            forceDirectory(target.getParent());
            byte[] committedBytes = readPlayerData(target);
            if (!installedRevision.equals(revision(committedBytes))) {
                throw new IOException(
                        "Committed playerdata did not match the validated temporary file for "
                                + playerId
                );
            }
            return committedBytes;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writeRecoveryBackup(Path target, byte[] source, UUID playerId)
            throws IOException {
        Path backup = target.resolveSibling(target.getFileName() + ".invtools-backup");
        Path temporary = Files.createTempFile(
                target.getParent(),
                playerId + ".invtools-backup-",
                ".dat"
        );
        try {
            copyPermissions(target, temporary);
            Files.write(temporary, source, StandardOpenOption.TRUNCATE_EXISTING);
            forceFile(temporary);
            moveAtomically(temporary, backup);
            forceDirectory(target.getParent());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    private static void forceFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    @FunctionalInterface
    private interface TemporaryPlayerDataVerifier {

        void verify(byte[] bytes, Path file) throws IOException;
    }

    private static void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            /*
             * Some supported platforms cannot open directories as FileChannels. The temporary
             * file itself was already forced and the atomic move is mandatory, so a directory
             * sync failure must not turn a completed replacement into an ambiguous failed save.
             */
        }
    }

    private static void copyPermissions(Path source, Path destination) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(
                source,
                PosixFileAttributeView.class
        );
        if (posix != null) {
            var attributes = posix.readAttributes();
            Files.setPosixFilePermissions(destination, attributes.permissions());
            Files.getFileAttributeView(destination, PosixFileAttributeView.class)
                    .setGroup(attributes.group());
            Files.getFileAttributeView(destination, FileOwnerAttributeView.class)
                    .setOwner(attributes.owner());
        }
    }

    private Object playerLock(UUID playerId) {
        return playerLocks[Math.floorMod(playerId.hashCode(), playerLocks.length)];
    }

    private static Object[] createPlayerLocks() {
        Object[] locks = new Object[PLAYER_LOCK_COUNT];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new Object();
        }
        return locks;
    }

    private record MigratedPlayerData(byte[] bytes, ReadWriteNBT root) {
        private MigratedPlayerData {
            bytes = bytes.clone();
            Objects.requireNonNull(root, "root");
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
