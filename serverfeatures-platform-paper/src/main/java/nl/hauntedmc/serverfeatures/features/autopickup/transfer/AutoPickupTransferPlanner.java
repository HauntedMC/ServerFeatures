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
                             ItemStack currentOffhand,
                             List<ItemStack> offeredDrops,
                             int inventoryMaxStackSize) {
        Objects.requireNonNull(currentStorage, "currentStorage");
        Objects.requireNonNull(offeredDrops, "offeredDrops");
        if (inventoryMaxStackSize <= 0) {
            throw new IllegalArgumentException("inventoryMaxStackSize must be positive");
        }

        ItemStack[] initialStorage = cloneArray(currentStorage);
        ItemStack[] changedStorage = cloneArray(initialStorage);
        ItemStack initialOffhand = cloneOrNull(currentOffhand);
        ItemStack changedOffhand = cloneOrNull(initialOffhand);
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
            OffhandResult offhandResult = fillOffhand(changedOffhand, remainder, inventoryMaxStackSize);
            changedOffhand = offhandResult.offhand();
            remainder = offhandResult.remainder();

            int remaining = remainder == null ? 0 : remainder.getAmount();
            int inserted = original.getAmount() - remaining;
            if (inserted < 0 || remaining < 0 || inserted + remaining != original.getAmount()) {
                throw new IllegalStateException("AutoPickup transfer plan violated item conservation");
            }
            results.add(new DropResult(original, remainder, inserted));
            totalInserted = Math.addExact(totalInserted, inserted);
            totalRemaining = Math.addExact(totalRemaining, remaining);
        }

        return new TransferPlan(
                initialStorage,
                changedStorage,
                initialOffhand,
                changedOffhand,
                results,
                totalInserted,
                totalRemaining
        );
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

    private OffhandResult fillOffhand(ItemStack currentOffhand,
                                      ItemStack remainder,
                                      int inventoryMaxStackSize) {
        if (remainder == null) {
            return new OffhandResult(currentOffhand, null);
        }
        if (currentOffhand != null && !currentOffhand.isSimilar(remainder)) {
            return new OffhandResult(currentOffhand, remainder);
        }

        int currentAmount = currentOffhand == null ? 0 : currentOffhand.getAmount();
        int limit = Math.min(remainder.getMaxStackSize(), inventoryMaxStackSize);
        int capacity = limit - currentAmount;
        if (capacity <= 0) {
            return new OffhandResult(currentOffhand, remainder);
        }

        int moved = Math.min(capacity, remainder.getAmount());
        ItemStack finalOffhand = currentOffhand == null
                ? withAmount(remainder, moved)
                : withAmount(currentOffhand, currentAmount + moved);
        return new OffhandResult(finalOffhand, withAmount(remainder, remainder.getAmount() - moved));
    }

    public static ItemStack[] cloneArray(ItemStack[] source) {
        Objects.requireNonNull(source, "source");
        return Arrays.stream(source).map(AutoPickupTransferPlanner::cloneOrNull).toArray(ItemStack[]::new);
    }

    public static ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0 ? null : item.clone();
    }

    static boolean sameStack(ItemStack first, ItemStack second) {
        ItemStack normalizedFirst = cloneOrNull(first);
        ItemStack normalizedSecond = cloneOrNull(second);
        if (normalizedFirst == null || normalizedSecond == null) {
            return normalizedFirst == normalizedSecond;
        }
        return normalizedFirst.getAmount() == normalizedSecond.getAmount()
                && normalizedFirst.isSimilar(normalizedSecond);
    }

    private static ItemStack withAmount(ItemStack item, int amount) {
        if (item == null || amount <= 0) {
            return null;
        }
        ItemStack clone = item.clone();
        clone.setAmount(amount);
        return clone;
    }

    private record OffhandResult(ItemStack offhand, ItemStack remainder) {
        private OffhandResult {
            offhand = cloneOrNull(offhand);
            remainder = cloneOrNull(remainder);
        }

        @Override
        public ItemStack offhand() {
            return cloneOrNull(offhand);
        }

        @Override
        public ItemStack remainder() {
            return cloneOrNull(remainder);
        }
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
            if (remainder != null && (original == null || !remainder.isSimilar(original))) {
                throw new IllegalArgumentException("DropResult remainder must match the original item");
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
            ItemStack[] initialStorage,
            ItemStack[] finalStorage,
            ItemStack initialOffhand,
            ItemStack finalOffhand,
            List<DropResult> drops,
            int totalInserted,
            int totalRemaining
    ) {
        public TransferPlan {
            initialStorage = cloneArray(initialStorage);
            finalStorage = cloneArray(finalStorage);
            initialOffhand = cloneOrNull(initialOffhand);
            finalOffhand = cloneOrNull(finalOffhand);
            drops = List.copyOf(drops);
            if (initialStorage.length != finalStorage.length) {
                throw new IllegalArgumentException("Initial and final storage lengths must match");
            }
            if (totalInserted < 0 || totalRemaining < 0) {
                throw new IllegalArgumentException("Transfer totals cannot be negative");
            }
            int calculatedInserted = 0;
            int calculatedRemaining = 0;
            for (DropResult drop : drops) {
                calculatedInserted = Math.addExact(calculatedInserted, drop.insertedAmount());
                calculatedRemaining = Math.addExact(calculatedRemaining, drop.remainingAmount());
            }
            if (calculatedInserted != totalInserted || calculatedRemaining != totalRemaining) {
                throw new IllegalArgumentException("Transfer totals do not match the per-drop results");
            }
        }

        @Override
        public ItemStack[] initialStorage() {
            return cloneArray(initialStorage);
        }

        @Override
        public ItemStack[] finalStorage() {
            return cloneArray(finalStorage);
        }

        @Override
        public ItemStack initialOffhand() {
            return cloneOrNull(initialOffhand);
        }

        @Override
        public ItemStack finalOffhand() {
            return cloneOrNull(finalOffhand);
        }

        public int remainingStacks() {
            return (int) drops.stream().filter(drop -> drop.remainingAmount() > 0).count();
        }
    }
}
