package nl.hauntedmc.serverfeatures.features.invtools.gui;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Objects;

/**
 * Deterministic shift-transfer operations for the staff member's 36 storage slots.
 *
 * <p>Main storage is filled before the hotbar, matching normal container shift-click behavior.
 * Every returned array and item is detached from the supplied Bukkit inventory state.</p>
 */
public final class PlayerStorageTransfer {

    private static final int[] INSERTION_ORDER = createInsertionOrder();

    private PlayerStorageTransfer() {
    }

    public static InsertionResult insert(ItemStack[] currentStorage, ItemStack offeredItem) {
        ItemStack[] changed = copyStorage(currentStorage);
        ItemStack remainder = cloneOrNull(offeredItem);
        if (remainder == null) {
            return new InsertionResult(changed, null);
        }

        for (int slot : INSERTION_ORDER) {
            ItemStack existing = changed[slot];
            if (existing == null || !existing.isSimilar(remainder)) {
                continue;
            }
            int capacity = existing.getMaxStackSize() - existing.getAmount();
            if (capacity <= 0) {
                continue;
            }
            int transferred = Math.min(capacity, remainder.getAmount());
            changed[slot] = withAmount(existing, existing.getAmount() + transferred);
            remainder = withAmount(remainder, remainder.getAmount() - transferred);
            if (remainder == null) {
                return new InsertionResult(changed, null);
            }
        }

        for (int slot : INSERTION_ORDER) {
            if (changed[slot] != null) {
                continue;
            }
            int transferred = Math.min(remainder.getMaxStackSize(), remainder.getAmount());
            changed[slot] = withAmount(remainder, transferred);
            remainder = withAmount(remainder, remainder.getAmount() - transferred);
            if (remainder == null) {
                return new InsertionResult(changed, null);
            }
        }
        return new InsertionResult(changed, remainder);
    }

    public static RemovalResult removeMatching(ItemStack[] currentStorage, ItemStack requestedItem) {
        ItemStack[] changed = copyStorage(currentStorage);
        ItemStack remainder = cloneOrNull(requestedItem);
        if (remainder == null) {
            return new RemovalResult(changed, null);
        }

        for (int slot : INSERTION_ORDER) {
            ItemStack existing = changed[slot];
            if (existing == null || !existing.isSimilar(remainder)) {
                continue;
            }
            int removed = Math.min(existing.getAmount(), remainder.getAmount());
            changed[slot] = withAmount(existing, existing.getAmount() - removed);
            remainder = withAmount(remainder, remainder.getAmount() - removed);
            if (remainder == null) {
                return new RemovalResult(changed, null);
            }
        }
        return new RemovalResult(changed, remainder);
    }

    public static ItemStack[] decrementSlot(ItemStack[] currentStorage, int slot, int amount) {
        ItemStack[] changed = copyStorage(currentStorage);
        if (slot < 0 || slot >= InventorySnapshot.STORAGE_SIZE) {
            throw new IllegalArgumentException("storage slot is out of bounds: " + slot);
        }
        ItemStack existing = changed[slot];
        if (existing == null || amount <= 0 || amount > existing.getAmount()) {
            throw new IllegalArgumentException("invalid storage decrement for slot " + slot);
        }
        changed[slot] = withAmount(existing, existing.getAmount() - amount);
        return changed;
    }

    public static boolean sameContents(ItemStack[] first, ItemStack[] second) {
        ItemStack[] left = copyStorage(first);
        ItemStack[] right = copyStorage(second);
        for (int slot = 0; slot < left.length; slot++) {
            if (!sameItem(left[slot], right[slot])) {
                return false;
            }
        }
        return true;
    }

    public static ItemStack[] copyStorage(ItemStack[] source) {
        Objects.requireNonNull(source, "source");
        if (source.length != InventorySnapshot.STORAGE_SIZE) {
            throw new IllegalArgumentException(
                    "storage must contain " + InventorySnapshot.STORAGE_SIZE + " slots"
            );
        }
        return Arrays.stream(source).map(PlayerStorageTransfer::cloneOrNull).toArray(ItemStack[]::new);
    }

    public static int transferredAmount(ItemStack offeredItem, ItemStack remainder) {
        ItemStack offered = cloneOrNull(offeredItem);
        ItemStack left = cloneOrNull(remainder);
        if (offered == null) {
            return 0;
        }
        return offered.getAmount() - (left == null ? 0 : left.getAmount());
    }

    private static int[] createInsertionOrder() {
        int[] order = new int[InventorySnapshot.STORAGE_SIZE];
        int index = 0;
        for (int slot = 9; slot < InventorySnapshot.STORAGE_SIZE; slot++) {
            order[index++] = slot;
        }
        for (int slot = 0; slot < 9; slot++) {
            order[index++] = slot;
        }
        return order;
    }

    private static boolean sameItem(ItemStack first, ItemStack second) {
        if (first == null || second == null) {
            return first == null && second == null;
        }
        return first.getAmount() == second.getAmount() && first.isSimilar(second);
    }

    private static ItemStack withAmount(ItemStack item, int amount) {
        if (item == null || amount <= 0) {
            return null;
        }
        ItemStack changed = item.clone();
        changed.setAmount(amount);
        return changed;
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0
                ? null
                : item.clone();
    }

    public record InsertionResult(ItemStack[] storage, ItemStack remainder) {
        public InsertionResult {
            storage = copyStorage(storage);
            remainder = cloneOrNull(remainder);
        }

        @Override
        public ItemStack[] storage() {
            return copyStorage(storage);
        }

        @Override
        public ItemStack remainder() {
            return cloneOrNull(remainder);
        }
    }

    public record RemovalResult(ItemStack[] storage, ItemStack remainder) {
        public RemovalResult {
            storage = copyStorage(storage);
            remainder = cloneOrNull(remainder);
        }

        @Override
        public ItemStack[] storage() {
            return copyStorage(storage);
        }

        @Override
        public ItemStack remainder() {
            return cloneOrNull(remainder);
        }
    }
}
