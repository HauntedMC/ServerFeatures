package nl.hauntedmc.serverfeatures.features.autopickup.transfer;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Pure detached inventory planning. No Bukkit inventory or entity is mutated here.
 */
public final class AutoPickupTransferPlanner {

    public TransferPlan plan(ItemStack[] currentStorage,
                             List<ItemStack> offeredDrops,
                             int inventoryMaxStackSize) {
        Objects.requireNonNull(currentStorage, "currentStorage");
        Objects.requireNonNull(offeredDrops, "offeredDrops");
        if (inventoryMaxStackSize <= 0) {
            throw new IllegalArgumentException("inventoryMaxStackSize must be positive");
        }

        ItemStack[] changedStorage = cloneArray(currentStorage);
        List<DropResult> results = new ArrayList<>(offeredDrops.size());
        int totalInserted = 0;
        int totalRemaining = 0;

        for (ItemStack offered : offeredDrops) {
            ItemStack original = cloneOrNull(offered);
            if (original == null) {
                results.add(new DropResult(null, null, 0));
                continue;
            }

            ItemStack remainder = original.clone();
            remainder = mergeIntoExisting(changedStorage, remainder, inventoryMaxStackSize);
            remainder = fillEmptySlots(changedStorage, remainder, inventoryMaxStackSize);

            int remaining = remainder == null ? 0 : remainder.getAmount();
            int inserted = original.getAmount() - remaining;
            if (inserted < 0 || remaining < 0 || inserted + remaining != original.getAmount()) {
                throw new IllegalStateException("AutoPickup transfer plan violated item conservation");
            }
            results.add(new DropResult(original, remainder, inserted));
            totalInserted = Math.addExact(totalInserted, inserted);
            totalRemaining = Math.addExact(totalRemaining, remaining);
        }

        return new TransferPlan(changedStorage, results, totalInserted, totalRemaining);
    }

    private ItemStack mergeIntoExisting(ItemStack[] storage,
                                        ItemStack remainder,
                                        int inventoryMaxStackSize) {
        for (int slot = 0; slot < storage.length && remainder != null; slot++) {
            ItemStack existing = storage[slot];
            if (existing == null || !existing.isSimilar(remainder)) {
                continue;
            }
            int limit = Math.min(existing.getMaxStackSize(), inventoryMaxStackSize);
            int capacity = limit - existing.getAmount();
            if (capacity <= 0) {
                continue;
            }
            int moved = Math.min(capacity, remainder.getAmount());
            storage[slot] = withAmount(existing, existing.getAmount() + moved);
            remainder = withAmount(remainder, remainder.getAmount() - moved);
        }
        return remainder;
    }

    private ItemStack fillEmptySlots(ItemStack[] storage,
                                     ItemStack remainder,
                                     int inventoryMaxStackSize) {
        for (int slot = 0; slot < storage.length && remainder != null; slot++) {
            if (storage[slot] != null) {
                continue;
            }
            int limit = Math.min(remainder.getMaxStackSize(), inventoryMaxStackSize);
            int moved = Math.min(limit, remainder.getAmount());
            storage[slot] = withAmount(remainder, moved);
            remainder = withAmount(remainder, remainder.getAmount() - moved);
        }
        return remainder;
    }

    public static ItemStack[] cloneArray(ItemStack[] source) {
        return Arrays.stream(source).map(AutoPickupTransferPlanner::cloneOrNull).toArray(ItemStack[]::new);
    }

    public static ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0 ? null : item.clone();
    }

    private static ItemStack withAmount(ItemStack item, int amount) {
        if (item == null || amount <= 0) {
            return null;
        }
        ItemStack clone = item.clone();
        clone.setAmount(amount);
        return clone;
    }

    public record DropResult(ItemStack original, ItemStack remainder, int insertedAmount) {
        public DropResult {
            original = cloneOrNull(original);
            remainder = cloneOrNull(remainder);
            if (insertedAmount < 0) {
                throw new IllegalArgumentException("insertedAmount cannot be negative");
            }
            int originalAmount = original == null ? 0 : original.getAmount();
            int remainingAmount = remainder == null ? 0 : remainder.getAmount();
            if (insertedAmount + remainingAmount != originalAmount) {
                throw new IllegalArgumentException("DropResult violates item conservation");
            }
        }

        @Override
        public ItemStack original() {
            return cloneOrNull(original);
        }

        @Override
        public ItemStack remainder() {
            return cloneOrNull(remainder);
        }

        public int remainingAmount() {
            return remainder == null ? 0 : remainder.getAmount();
        }
    }

    public record TransferPlan(
            ItemStack[] finalStorage,
            List<DropResult> drops,
            int totalInserted,
            int totalRemaining
    ) {
        public TransferPlan {
            finalStorage = cloneArray(finalStorage);
            drops = List.copyOf(drops);
            if (totalInserted < 0 || totalRemaining < 0) {
                throw new IllegalArgumentException("Transfer totals cannot be negative");
            }
        }

        @Override
        public ItemStack[] finalStorage() {
            return cloneArray(finalStorage);
        }

        public int remainingStacks() {
            return (int) drops.stream().filter(drop -> drop.remainingAmount() > 0).count();
        }
    }
}
