package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.NBTType;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBTCompoundList;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Reads only the vanilla playerdata file and mutates only Inventory, EnderItems, and the five
 * player equipment entries owned by InvTools.
 */
public final class NbtOfflinePlayerDataStore implements OfflinePlayerDataStore {

    public static final int EQUIPMENT_COMPOUND_DATA_VERSION = 4325;
    /**
     * The DataVersion written by the Paper runtime declared in this build. Offline writes are
     * deliberately exact-version only: Item-NBT-API can upgrade item data for inspection, but it
     * cannot safely downgrade current item components back into an older playerdata schema.
     */
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

        readInventory(root, storage, equipment, equipmentFormat);
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
                writeInventory(root, original.snapshot(), changedSnapshot, original.equipmentStorageFormat());
            } else {
                writeEnderChest(root, original.snapshot(), changedSnapshot);
            }
            writeAtomically(file, root, original.revision(), playerId);
        }
    }

    private void readInventory(
            ReadableNBT root,
            ItemStack[] storage,
            ItemStack[] equipment,
            EquipmentStorageFormat format
    ) throws IOException {
        validateSlottedList(root, "Inventory", "Inventory", InventorySnapshot.STORAGE_SIZE, format);
        for (ReadWriteNBT entry : root.getCompoundList("Inventory")) {
            int slot = slot(entry, "Inventory");
            if (slot >= 0 && slot < storage.length) {
                storage[slot] = decodeItem(entry, "Inventory slot " + slot);
            } else if (format == EquipmentStorageFormat.LEGACY_INVENTORY_SLOTS) {
                switch (slot) {
                    case InventorySnapshot.HELMET_SLOT ->
                            equipment[0] = decodeItem(entry, "helmet");
                    case InventorySnapshot.CHESTPLATE_SLOT ->
                            equipment[1] = decodeItem(entry, "chestplate");
                    case InventorySnapshot.LEGGINGS_SLOT ->
                            equipment[2] = decodeItem(entry, "leggings");
                    case InventorySnapshot.BOOTS_SLOT ->
                            equipment[3] = decodeItem(entry, "boots");
                    case InventorySnapshot.OFF_HAND_SLOT ->
                            equipment[4] = decodeItem(entry, "offhand");
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
                        "helmet"
                );
                equipment[1] = decodeOptionalItem(
                        modernEquipment.getCompound("chest"),
                        "chestplate"
                );
                equipment[2] = decodeOptionalItem(
                        modernEquipment.getCompound("legs"),
                        "leggings"
                );
                equipment[3] = decodeOptionalItem(
                        modernEquipment.getCompound("feet"),
                        "boots"
                );
                equipment[4] = decodeOptionalItem(
                        modernEquipment.getCompound("offhand"),
                        "offhand"
                );
            }
        }
    }

    private void readEnderChest(
            ReadableNBT root,
            ItemStack[] enderChest
    ) throws IOException {
        validateSlottedList(root, "EnderItems", "EnderItems", InventorySnapshot.ENDER_CHEST_SIZE, null);
        for (ReadWriteNBT entry : root.getCompoundList("EnderItems")) {
            int slot = slot(entry, "EnderItems");
            if (slot >= 0 && slot < enderChest.length) {
                enderChest[slot] = decodeItem(entry, "EnderItems slot " + slot);
            }
        }
    }

    private void writeInventory(
            ReadWriteNBT root,
            InventorySnapshot original,
            InventorySnapshot changed,
            EquipmentStorageFormat format
    ) {
        ReadWriteNBTCompoundList inventory = root.getCompoundList("Inventory");
        for (int slot = 0; slot < InventorySnapshot.STORAGE_SIZE; slot++) {
            patchSlottedItem(inventory, slot,
                    original.itemAt(InventoryKind.PLAYER, slot),
                    changed.itemAt(InventoryKind.PLAYER, slot));
        }

        if (format == EquipmentStorageFormat.LEGACY_INVENTORY_SLOTS) {
            patchSlottedItem(inventory, InventorySnapshot.HELMET_SLOT, original.helmet(), changed.helmet());
            patchSlottedItem(inventory, InventorySnapshot.CHESTPLATE_SLOT,
                    original.chestplate(), changed.chestplate());
            patchSlottedItem(inventory, InventorySnapshot.LEGGINGS_SLOT,
                    original.leggings(), changed.leggings());
            patchSlottedItem(inventory, InventorySnapshot.BOOTS_SLOT, original.boots(), changed.boots());
            patchSlottedItem(inventory, InventorySnapshot.OFF_HAND_SLOT, original.offHand(), changed.offHand());
            return;
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
            return decoded.clone();
        } catch (RuntimeException exception) {
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

    private static void validateSlottedList(
            ReadableNBT root,
            String key,
            String location,
            int regularSlotCount,
            EquipmentStorageFormat equipmentFormat
    ) throws IOException {
        if (!root.hasTag(key, NBTType.NBTTagList)
                || root.getListType(key) != NBTType.NBTTagCompound) {
            throw new IOException("Playerdata " + location + " is not a compound list");
        }
        Set<Integer> slots = new HashSet<>();
        for (ReadWriteNBT entry : root.getCompoundList(key)) {
            int slot = slot(entry, location);
            boolean supported = slot >= 0 && slot < regularSlotCount;
            if (equipmentFormat == EquipmentStorageFormat.LEGACY_INVENTORY_SLOTS) {
                supported |= slot == InventorySnapshot.BOOTS_SLOT
                        || slot == InventorySnapshot.LEGGINGS_SLOT
                        || slot == InventorySnapshot.CHESTPLATE_SLOT
                        || slot == InventorySnapshot.HELMET_SLOT
                        || slot == InventorySnapshot.OFF_HAND_SLOT;
            }
            if (!supported) {
                continue;
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
