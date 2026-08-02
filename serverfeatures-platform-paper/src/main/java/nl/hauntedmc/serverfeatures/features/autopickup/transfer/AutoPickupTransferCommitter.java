package nl.hauntedmc.serverfeatures.features.autopickup.transfer;

import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Applies one detached transfer plan and restores both sides when any mutation fails.
 */
public final class AutoPickupTransferCommitter {

    public void commit(PlayerInventory inventory,
                       BlockDropItemEvent event,
                       List<Item> eligibleItems,
                       AutoPickupTransferPlanner.TransferPlan plan) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(eligibleItems, "eligibleItems");
        Objects.requireNonNull(plan, "plan");
        if (eligibleItems.size() != plan.drops().size()) {
            throw new IllegalArgumentException("Eligible item count does not match transfer plan");
        }

        ItemStack[] originalStorage = AutoPickupTransferPlanner.cloneArray(inventory.getStorageContents());
        List<Item> originalEventItems = new ArrayList<>(event.getItems());
        Map<Item, ItemStack> originalStacks = new IdentityHashMap<>();
        for (Item item : originalEventItems) {
            originalStacks.put(item, AutoPickupTransferPlanner.cloneOrNull(item.getItemStack()));
        }

        try {
            inventory.setStorageContents(plan.finalStorage());
            for (int index = 0; index < eligibleItems.size(); index++) {
                Item item = eligibleItems.get(index);
                AutoPickupTransferPlanner.DropResult result = plan.drops().get(index);
                ItemStack remainder = result.remainder();
                if (remainder == null) {
                    if (!event.getItems().remove(item)) {
                        throw new IllegalStateException("AutoPickup drop disappeared before commit");
                    }
                } else {
                    item.setItemStack(remainder);
                }
            }
            if (!sameStorage(inventory.getStorageContents(), plan.finalStorage())) {
                throw new IllegalStateException("Player inventory did not retain the planned AutoPickup state");
            }
        } catch (Throwable commitFailure) {
            try {
                rollback(inventory, event, originalStorage, originalEventItems, originalStacks);
            } catch (Throwable rollbackFailure) {
                commitFailure.addSuppressed(rollbackFailure);
                throw new AutoPickupCommitException(
                        "AutoPickup commit and rollback both failed",
                        commitFailure,
                        true
                );
            }
            throw new AutoPickupCommitException("AutoPickup commit failed and was rolled back", commitFailure, false);
        }
    }

    private void rollback(PlayerInventory inventory,
                          BlockDropItemEvent event,
                          ItemStack[] originalStorage,
                          List<Item> originalEventItems,
                          Map<Item, ItemStack> originalStacks) {
        inventory.setStorageContents(AutoPickupTransferPlanner.cloneArray(originalStorage));
        event.getItems().clear();
        event.getItems().addAll(originalEventItems);
        for (Item item : originalEventItems) {
            ItemStack original = originalStacks.get(item);
            if (original != null) {
                item.setItemStack(original.clone());
            }
        }
        if (!sameStorage(inventory.getStorageContents(), originalStorage)) {
            throw new IllegalStateException("AutoPickup inventory rollback verification failed");
        }
    }

    private boolean sameStorage(ItemStack[] first, ItemStack[] second) {
        if (first.length != second.length) {
            return false;
        }
        for (int slot = 0; slot < first.length; slot++) {
            ItemStack left = first[slot];
            ItemStack right = second[slot];
            if (left == null || right == null) {
                if (left != null || right != null) {
                    return false;
                }
                continue;
            }
            if (left.getAmount() != right.getAmount() || !left.isSimilar(right)) {
                return false;
            }
        }
        return true;
    }

    public static final class AutoPickupCommitException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final boolean rollbackFailed;

        AutoPickupCommitException(String message, Throwable cause, boolean rollbackFailed) {
            super(message, cause);
            this.rollbackFailed = rollbackFailed;
        }

        public boolean rollbackFailed() {
            return rollbackFailed;
        }
    }
}
