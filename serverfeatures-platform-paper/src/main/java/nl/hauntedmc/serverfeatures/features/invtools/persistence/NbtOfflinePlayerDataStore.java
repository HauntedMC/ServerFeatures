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
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * Reads only the vanilla playerdata file and mutates only Inventory, EnderItems, and the five
 * player equipment entries owned by InvTools.
 */
public final class NbtOfflinePlayerDataStore implements OfflinePlayerDataStore {

    private static final int MAX_COMPRESSED_PLAYER_DATA_BYTES = 4 * 1024 * 1024;
    private static final int MAX_DECOMPRESSED_PLAYER_DATA_BYTES = 32 * 1024 * 1024;
    private static final int PLAYER_LOCK_COUNT = 64;

    private final Path playerDataDirectory;
    private final int runtimeDataVersion;
    private final Object[] playerLocks = createPlayerLocks();

    public NbtOfflinePlayerDataStore(Path levelDirectory) {
        this(levelDirectory, DataFixerUtil.getCurrentVersion());
    }

    NbtOfflinePlayerDataStore(Path levelDirectory, int runtimeDataVersion) {
        this.playerDataDirectory = levelDirectory.toAbsolutePath().normalize().resolve("playerdata");
        if (runtimeDataVersion <= 0) {
            throw new IllegalArgumentException("runtimeDataVersion must be positive");
        }
        this.runtimeDataVersion = runtimeDataVersion;
    }

    @Override
    public boolean hasPlayerData(UUID playerId) throws IOException {
        Path file = playerFile(playerId);
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file);
    }

    @Override
    public OfflinePlayerData load(UUID playerId) throws IOException {
        Path file = playerFile(playerId);
        byte[] bytes = readPlayerData(file);
        ReadWriteNBT root = readNbt(bytes, file);
        validatePlayerIdentity(root, playerId);
        requireCurrentDataVersion(root, playerId);

        ItemStack[] storage = new ItemStack[InventorySnapshot.STORAGE_SIZE];
        ItemStack[] enderChest = new ItemStack[InventorySnapshot.ENDER_CHEST_SIZE];
        ItemStack[] equipment = new ItemStack[5];

        readInventory(root, storage, equipment);
        readEnderChest(root, enderChest);

        InventorySnapshot snapshot = new InventorySnapshot(
                storage,
                equipment[0],
                equipment[1],
                equipment[2],
                equipment[3],
                equipment[4],
                enderChest
        );
        return new OfflinePlayerData(
                playerId,
                snapshot,
                revision(bytes)
        );
    }

    @Override
    public void save(
            OfflinePlayerData original,
            InventoryKind kind,
            InventorySnapshot changedSnapshot
    ) throws IOException {
        UUID playerId = original.playerId();
        synchronized (playerLock(playerId)) {
            Path file = playerFile(playerId);
            byte[] currentBytes = readPlayerData(file);
            requireRevision(original.revision(), currentBytes, playerId);

            ReadWriteNBT root = readNbt(currentBytes, file);
            validatePlayerIdentity(root, playerId);
            if (root.getOrDefault("DataVersion", 0) != runtimeDataVersion) {
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
    }

    private void readInventory(
            ReadableNBT root,
            ItemStack[] storage,
            ItemStack[] equipment
    ) throws IOException {
        validateSlottedList(root, "Inventory", "Inventory", InventorySnapshot.STORAGE_SIZE);
        for (ReadWriteNBT entry : root.getCompoundList("Inventory")) {
            int slot = slot(entry, "Inventory");
            storage[slot] = decodeItem(entry, "Inventory slot " + slot);
        }

        validateCurrentEquipment(root);
        ReadableNBT equipmentData = root.getCompound("equipment");
        if (equipmentData != null) {
            equipment[0] = decodeOptionalItem(equipmentData.getCompound("head"), "helmet");
            equipment[1] = decodeOptionalItem(equipmentData.getCompound("chest"), "chestplate");
            equipment[2] = decodeOptionalItem(equipmentData.getCompound("legs"), "leggings");
            equipment[3] = decodeOptionalItem(equipmentData.getCompound("feet"), "boots");
            equipment[4] = decodeOptionalItem(equipmentData.getCompound("offhand"), "offhand");
        }
    }

    private void readEnderChest(
            ReadableNBT root,
            ItemStack[] enderChest
    ) throws IOException {
        validateSlottedList(root, "EnderItems", "EnderItems", InventorySnapshot.ENDER_CHEST_SIZE);
        for (ReadWriteNBT entry : root.getCompoundList("EnderItems")) {
            int slot = slot(entry, "EnderItems");
            enderChest[slot] = decodeItem(entry, "EnderItems slot " + slot);
        }
    }

    private void writeInventory(
            ReadWriteNBT root,
            InventorySnapshot original,
            InventorySnapshot changed
    ) {
        ReadWriteNBTCompoundList inventory = root.getCompoundList("Inventory");
        for (int slot = 0; slot < InventorySnapshot.STORAGE_SIZE; slot++) {
            patchSlottedItem(inventory, slot,
                    original.itemAt(InventoryKind.PLAYER, slot),
                    changed.itemAt(InventoryKind.PLAYER, slot));
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
            patchSlottedItem(enderItems, slot,
                    original.itemAt(InventoryKind.ENDER_CHEST, slot),
                    changed.itemAt(InventoryKind.ENDER_CHEST, slot));
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
            ReadableNBT item,
            String location
    ) throws IOException {
        return item == null || item.isEmpty() ? null : decodeItem(item, location);
    }

    private static ItemStack decodeItem(
            ReadableNBT item,
            String location
    ) throws IOException {
        try {
            ItemStack decoded = NBT.itemStackFromNBT(item);
            if (isEmpty(decoded)) {
                return null;
            }
            if (decoded.getAmount() > decoded.getMaxStackSize()) {
                throw new IOException(
                        "Player item at " + location + " exceeds its legal stack size"
                );
            }
            return decoded.clone();
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("Could not decode player item at " + location, exception);
        }
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
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
                throw new IOException("Playerdata " + location + " contains duplicate slot " + slot);
            }
        }
    }

    private static int slot(ReadableNBT entry, String location) throws IOException {
        if (!entry.hasTag("Slot", NBTType.NBTTagByte)) {
            throw new IOException("Playerdata " + location + " entry is missing its byte Slot tag");
        }
        return entry.getByte("Slot");
    }

    private static void validateCurrentEquipment(ReadableNBT root) throws IOException {
        if (root.hasTag("equipment") && !root.hasTag("equipment", NBTType.NBTTagCompound)) {
            throw new IOException("Playerdata equipment is not a compound");
        }
        ReadableNBT equipment = root.getCompound("equipment");
        if (equipment == null) {
            return;
        }
        for (String key : Set.of("head", "chest", "legs", "feet", "offhand")) {
            if (equipment.hasTag(key) && !equipment.hasTag(key, NBTType.NBTTagCompound)) {
                throw new IOException("Playerdata equipment." + key + " is not a compound");
            }
        }
    }

    private void requireCurrentDataVersion(ReadableNBT root, UUID playerId) throws IOException {
        int dataVersion = root.getOrDefault("DataVersion", 0);
        if (dataVersion != runtimeDataVersion) {
            throw new IOException(
                    "Playerdata version " + dataVersion + " does not match the running Paper version "
                            + runtimeDataVersion + " for " + playerId
            );
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

    private static byte[] readPlayerData(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IOException("Playerdata file does not exist or is not a regular file: " + file.getFileName());
        }
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

    private static ReadWriteNBT readNbt(byte[] bytes, Path file) throws IOException {
        try {
            return NBT.readNBT(new ByteArrayInputStream(bytes));
        } catch (RuntimeException exception) {
            throw new IOException("Could not parse playerdata file " + file.getFileName(), exception);
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

    private static void writeAtomically(
            Path target,
            ReadWriteNBT root,
            PlayerDataRevision expected,
            UUID playerId
    ) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), playerId + ".invtools-", ".dat");
        try {
            copyPermissions(target, temporary);
            NBT.writeFile(temporary.toFile(), root);
            forceFile(temporary);

            byte[] currentBytes = readPlayerData(target);
            requireRevision(expected, currentBytes, playerId);
            writeRecoveryBackup(target, currentBytes, playerId);
            moveAtomically(temporary, target);
            forceDirectory(target.getParent());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writeRecoveryBackup(Path target, byte[] source, UUID playerId) throws IOException {
        Path backup = target.resolveSibling(target.getFileName() + ".invtools-backup");
        Path temporary = Files.createTempFile(target.getParent(), playerId + ".invtools-backup-", ".dat");
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
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void forceFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
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
        PosixFileAttributeView posix = Files.getFileAttributeView(source, PosixFileAttributeView.class);
        if (posix != null) {
            var attributes = posix.readAttributes();
            Files.setPosixFilePermissions(destination, attributes.permissions());
            Files.getFileAttributeView(destination, PosixFileAttributeView.class).setGroup(attributes.group());
            Files.getFileAttributeView(destination, FileOwnerAttributeView.class).setOwner(attributes.owner());
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
}
