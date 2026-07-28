package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBTCompoundList;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import de.tr7zw.changeme.nbtapi.utils.DataFixerUtil;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/**
 * Reads only the vanilla playerdata file and mutates only Inventory, EnderItems, and the five
 * player equipment entries owned by InvTools.
 */
public final class NbtOfflinePlayerDataStore implements OfflinePlayerDataStore {

    public static final int EQUIPMENT_COMPOUND_DATA_VERSION = 4325;
    public static final int CURRENT_SUPPORTED_DATA_VERSION = 4903;
    private static final int MAX_COMPRESSED_PLAYER_DATA_BYTES = 16 * 1024 * 1024;
    private static final int PLAYER_LOCK_COUNT = 64;

    private final Path playerDataDirectory;
    private final Object[] playerLocks = createPlayerLocks();

    public NbtOfflinePlayerDataStore(Path levelDirectory) {
        this.playerDataDirectory = levelDirectory.toAbsolutePath().normalize().resolve("playerdata");
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
        int dataVersion = root.getOrDefault("DataVersion", 0);
        EquipmentStorageFormat equipmentFormat = equipmentFormat(root);

        ItemStack[] storage = new ItemStack[InventorySnapshot.STORAGE_SIZE];
        ItemStack[] enderChest = new ItemStack[InventorySnapshot.ENDER_CHEST_SIZE];
        ItemStack[] equipment = new ItemStack[5];

        readInventory(root, storage, equipment, equipmentFormat, dataVersion);
        readEnderChest(root, enderChest, dataVersion);

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
                revision(bytes),
                dataVersion,
                equipmentFormat
        );
    }

    @Override
    public void save(
            OfflinePlayerData original,
            InventoryKind kind,
            InventorySnapshot changedSnapshot
    ) throws IOException {
        if (!original.supportsSafeEditing()) {
            throw new IOException(
                    "Refusing to write unsupported playerdata version " + original.dataVersion()
            );
        }
        UUID playerId = original.playerId();
        synchronized (playerLock(playerId)) {
            Path file = playerFile(playerId);
            byte[] currentBytes = readPlayerData(file);
            requireRevision(original.revision(), currentBytes, playerId);

            ReadWriteNBT root = readNbt(currentBytes, file);
            if (kind == InventoryKind.PLAYER) {
                writeInventory(root, changedSnapshot, original.equipmentStorageFormat());
            } else {
                writeEnderChest(root, changedSnapshot);
            }
            writeAtomically(file, root, original.revision(), playerId);
        }
    }

    private void readInventory(
            ReadableNBT root,
            ItemStack[] storage,
            ItemStack[] equipment,
            EquipmentStorageFormat format,
            int dataVersion
    ) throws IOException {
        for (ReadWriteNBT entry : root.getCompoundList("Inventory")) {
            int slot = entry.getByte("Slot");
            if (slot >= 0 && slot < storage.length) {
                storage[slot] = decodeItem(entry, "Inventory slot " + slot, dataVersion);
            } else if (format == EquipmentStorageFormat.LEGACY_INVENTORY_SLOTS) {
                switch (slot) {
                    case InventorySnapshot.HELMET_SLOT ->
                            equipment[0] = decodeItem(entry, "helmet", dataVersion);
                    case InventorySnapshot.CHESTPLATE_SLOT ->
                            equipment[1] = decodeItem(entry, "chestplate", dataVersion);
                    case InventorySnapshot.LEGGINGS_SLOT ->
                            equipment[2] = decodeItem(entry, "leggings", dataVersion);
                    case InventorySnapshot.BOOTS_SLOT ->
                            equipment[3] = decodeItem(entry, "boots", dataVersion);
                    case InventorySnapshot.OFF_HAND_SLOT ->
                            equipment[4] = decodeItem(entry, "offhand", dataVersion);
                    default -> {
                    }
                }
            }
        }

        if (format == EquipmentStorageFormat.EQUIPMENT_COMPOUND) {
            ReadableNBT modernEquipment = root.getCompound("equipment");
            if (modernEquipment != null) {
                equipment[0] = decodeOptionalItem(
                        modernEquipment.getCompound("head"),
                        "helmet",
                        dataVersion
                );
                equipment[1] = decodeOptionalItem(
                        modernEquipment.getCompound("chest"),
                        "chestplate",
                        dataVersion
                );
                equipment[2] = decodeOptionalItem(
                        modernEquipment.getCompound("legs"),
                        "leggings",
                        dataVersion
                );
                equipment[3] = decodeOptionalItem(
                        modernEquipment.getCompound("feet"),
                        "boots",
                        dataVersion
                );
                equipment[4] = decodeOptionalItem(
                        modernEquipment.getCompound("offhand"),
                        "offhand",
                        dataVersion
                );
            }
        }
    }

    private void readEnderChest(
            ReadableNBT root,
            ItemStack[] enderChest,
            int dataVersion
    ) throws IOException {
        for (ReadWriteNBT entry : root.getCompoundList("EnderItems")) {
            int slot = entry.getByte("Slot");
            if (slot >= 0 && slot < enderChest.length) {
                enderChest[slot] = decodeItem(entry, "EnderItems slot " + slot, dataVersion);
            }
        }
    }

    private void writeInventory(
            ReadWriteNBT root,
            InventorySnapshot snapshot,
            EquipmentStorageFormat format
    ) {
        ReadWriteNBTCompoundList inventory = root.getCompoundList("Inventory");
        inventory.removeIf(entry -> isManagedInventorySlot(entry.getByte("Slot")));

        ItemStack[] storage = snapshot.storage();
        for (int slot = 0; slot < storage.length; slot++) {
            addSlottedItem(inventory, slot, storage[slot]);
        }

        if (format == EquipmentStorageFormat.LEGACY_INVENTORY_SLOTS) {
            addSlottedItem(inventory, InventorySnapshot.HELMET_SLOT, snapshot.helmet());
            addSlottedItem(inventory, InventorySnapshot.CHESTPLATE_SLOT, snapshot.chestplate());
            addSlottedItem(inventory, InventorySnapshot.LEGGINGS_SLOT, snapshot.leggings());
            addSlottedItem(inventory, InventorySnapshot.BOOTS_SLOT, snapshot.boots());
            addSlottedItem(inventory, InventorySnapshot.OFF_HAND_SLOT, snapshot.offHand());
            return;
        }

        ReadWriteNBT equipment = root.getOrCreateCompound("equipment");
        setEquipmentItem(equipment, "head", snapshot.helmet());
        setEquipmentItem(equipment, "chest", snapshot.chestplate());
        setEquipmentItem(equipment, "legs", snapshot.leggings());
        setEquipmentItem(equipment, "feet", snapshot.boots());
        setEquipmentItem(equipment, "offhand", snapshot.offHand());
    }

    private void writeEnderChest(ReadWriteNBT root, InventorySnapshot snapshot) {
        ReadWriteNBTCompoundList enderItems = root.getCompoundList("EnderItems");
        enderItems.removeIf(entry -> {
            int slot = entry.getByte("Slot");
            return slot >= 0 && slot < InventorySnapshot.ENDER_CHEST_SIZE;
        });

        ItemStack[] items = snapshot.enderChest();
        for (int slot = 0; slot < items.length; slot++) {
            addSlottedItem(enderItems, slot, items[slot]);
        }
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

    private static void setEquipmentItem(ReadWriteNBT equipment, String key, ItemStack item) {
        if (isEmpty(item)) {
            equipment.removeKey(key);
            return;
        }
        ReadWriteNBT target = equipment.getOrCreateCompound(key);
        target.clearNBT();
        target.mergeCompound(NBT.itemStackToNBT(item));
    }

    private static boolean isManagedInventorySlot(int slot) {
        if (slot >= 0 && slot < InventorySnapshot.STORAGE_SIZE) {
            return true;
        }
        return slot == InventorySnapshot.BOOTS_SLOT
                || slot == InventorySnapshot.LEGGINGS_SLOT
                || slot == InventorySnapshot.CHESTPLATE_SLOT
                || slot == InventorySnapshot.HELMET_SLOT
                || slot == InventorySnapshot.OFF_HAND_SLOT;
    }

    private static ItemStack decodeOptionalItem(
            ReadableNBT item,
            String location,
            int dataVersion
    ) throws IOException {
        return item == null || item.isEmpty() ? null : decodeItem(item, location, dataVersion);
    }

    private static ItemStack decodeItem(
            ReadableNBT item,
            String location,
            int dataVersion
    ) throws IOException {
        try {
            ReadableNBT readableItem = item;
            if (dataVersion > 0 && dataVersion < DataFixerUtil.getCurrentVersion()) {
                ReadWriteNBT copy = NBT.parseNBT(item.toString());
                readableItem = DataFixerUtil.fixUpItemData(copy, dataVersion);
            }
            ItemStack decoded = NBT.itemStackFromNBT(readableItem);
            if (isEmpty(decoded)) {
                return null;
            }
            return decoded.clone();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            throw new IOException("Could not decode player item at " + location, exception);
        }
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static EquipmentStorageFormat equipmentFormat(ReadableNBT root) {
        int dataVersion = root.getOrDefault("DataVersion", 0);
        return dataVersion >= EQUIPMENT_COMPOUND_DATA_VERSION
                ? EquipmentStorageFormat.EQUIPMENT_COMPOUND
                : EquipmentStorageFormat.LEGACY_INVENTORY_SLOTS;
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
            return bytes;
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
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }

            requireRevision(expected, readPlayerData(target), playerId);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void copyPermissions(Path source, Path destination) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(source);
            Files.setPosixFilePermissions(destination, permissions);
        } catch (IOException | UnsupportedOperationException ignored) {
            // The server's default file permissions are acceptable on non-POSIX file systems.
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
