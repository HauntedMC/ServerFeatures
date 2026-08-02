package nl.hauntedmc.serverfeatures.features.autopickup.transfer;

import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

        ItemStack[] originalStorage;
        List<Item> originalEventItems;
        Map<Item, ItemStack> originalStacks;
        try {
            originalStorage = cloneExactArray(inventory.getStorageContents());
            originalEventItems = new ArrayList<>(event.getItems());
            originalStacks = snapshotStacks(originalEventItems);
            validatePreconditions(originalStorage, originalEventItems, eligibleItems, plan);
        } catch (RuntimeException preconditionFailure) {
            throw new AutoPickupCommitException(
                    "AutoPickup commit precondition failed before mutation",
                    preconditionFailure,
                    false
            );
        }

        ItemStack[] plannedFinalStorage = plan.finalStorage();
        try {
            inventory.setStorageContents(plannedFinalStorage);
            for (int index = 0; index < eligibleItems.size(); index++) {
                Item item = eligibleItems.get(index);
                AutoPickupTransferPlanner.DropResult result = plan.drops().get(index);
                ItemStack remainder = result.remainder();
                if (remainder == null) {
                    if (!removeIdentity(event.getItems(), item)) {
                        throw new IllegalStateException("AutoPickup drop disappeared before commit");
                    }
                } else {
                    item.setItemStack(remainder);
                }
            }
            verifyCommittedState(
                    inventory,
                    event,
                    eligibleItems,
                    plan,
                    originalEventItems,
                    originalStacks,
                    plannedFinalStorage
            );
        } catch (Throwable commitFailure) {
            try {
                rollback(inventory, event, originalStorage, originalEventItems, originalStacks);
            } catch (Throwable rollbackFailure) {
                commitFailure.addSuppressed(rollbackFailure);
                if (commitFailure instanceof VirtualMachineError fatal) {
                    throw fatal;
                }
                if (commitFailure instanceof ThreadDeath threadDeath) {
                    throw threadDeath;
                }
                throw new AutoPickupCommitException(
                        "AutoPickup commit and rollback both failed",
                        commitFailure,
                        true
                );
            }
            if (commitFailure instanceof VirtualMachineError fatal) {
                throw fatal;
            }
            if (commitFailure instanceof ThreadDeath threadDeath) {
                throw threadDeath;
            }
            throw new AutoPickupCommitException("AutoPickup commit failed and was rolled back", commitFailure, false);
        }
    }

    private void validatePreconditions(ItemStack[] liveStorage,
                                       List<Item> eventItems,
                                       List<Item> eligibleItems,
                                       AutoPickupTransferPlanner.TransferPlan plan) {
        if (!sameStorage(liveStorage, plan.initialStorage())) {
            throw new IllegalStateException("Player storage changed after AutoPickup planning");
        }

        Set<Item> uniqueEligible = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int index = 0; index < eligibleItems.size(); index++) {
            Item item = eligibleItems.get(index);
            if (!uniqueEligible.add(item)) {
                throw new IllegalStateException("The same AutoPickup item entity was offered more than once");
            }
            if (countIdentity(eventItems, item) != 1) {
                throw new IllegalStateException("Eligible AutoPickup item is not uniquely present in the event");
            }
            ItemStack expectedOriginal = plan.drops().get(index).original();
            if (!AutoPickupTransferPlanner.sameStack(item.getItemStack(), expectedOriginal)) {
                throw new IllegalStateException("AutoPickup item stack changed after planning");
            }
        }
    }

    private void verifyCommittedState(PlayerInventory inventory,
                                      BlockDropItemEvent event,
                                      List<Item> eligibleItems,
                                      AutoPickupTransferPlanner.TransferPlan plan,
                                      List<Item> originalEventItems,
                                      Map<Item, ItemStack> originalStacks,
                                      ItemStack[] plannedFinalStorage) {
        if (!sameStorage(inventory.getStorageContents(), plannedFinalStorage)) {
            throw new IllegalStateException("Player inventory did not retain the planned AutoPickup state");
        }

        List<Item> expectedEventItems = new ArrayList<>(originalEventItems);
        Map<Item, ItemStack> expectedStacks = new IdentityHashMap<>(originalStacks);
        for (int index = 0; index < eligibleItems.size(); index++) {
            Item item = eligibleItems.get(index);
            ItemStack remainder = plan.drops().get(index).remainder();
            if (remainder == null) {
                if (!removeIdentity(expectedEventItems, item)) {
                    throw new IllegalStateException("Eligible AutoPickup item was absent from the original event");
                }
                expectedStacks.remove(item);
            } else {
                expectedStacks.put(item, remainder);
            }
        }

        if (!sameIdentityOrder(event.getItems(), expectedEventItems)) {
            throw new IllegalStateException("AutoPickup event membership or item order did not match the plan");
        }
        verifyStacks(expectedEventItems, expectedStacks, "AutoPickup event stack did not match the plan");
    }

    private void rollback(PlayerInventory inventory,
                          BlockDropItemEvent event,
                          ItemStack[] originalStorage,
                          List<Item> originalEventItems,
                          Map<Item, ItemStack> originalStacks) {
        inventory.setStorageContents(cloneExactArray(originalStorage));
        event.getItems().clear();
        event.getItems().addAll(originalEventItems);
        for (Item item : originalEventItems) {
            item.setItemStack(cloneExact(originalStacks.get(item)));
        }

        if (!sameStorage(inventory.getStorageContents(), originalStorage)) {
            throw new IllegalStateException("AutoPickup inventory rollback verification failed");
        }
        if (!sameIdentityOrder(event.getItems(), originalEventItems)) {
            throw new IllegalStateException("AutoPickup event-list rollback verification failed");
        }
        verifyStacks(
                originalEventItems,
                originalStacks,
                "AutoPickup item-stack rollback verification failed"
        );
    }

    private Map<Item, ItemStack> snapshotStacks(List<Item> items) {
        Map<Item, ItemStack> snapshots = new IdentityHashMap<>();
        for (Item item : items) {
            snapshots.put(item, cloneExact(item.getItemStack()));
        }
        return snapshots;
    }

    private void verifyStacks(List<Item> items,
                              Map<Item, ItemStack> expectedStacks,
                              String failureMessage) {
        for (Item item : items) {
            ItemStack expected = expectedStacks.get(item);
            if (expected == null || !AutoPickupTransferPlanner.sameStack(item.getItemStack(), expected)) {
                throw new IllegalStateException(failureMessage);
            }
        }
    }

    private boolean sameStorage(ItemStack[] first, ItemStack[] second) {
        if (first.length != second.length) {
            return false;
        }
        for (int slot = 0; slot < first.length; slot++) {
            if (!AutoPickupTransferPlanner.sameStack(first[slot], second[slot])) {
                return false;
            }
        }
        return true;
    }

    private boolean sameIdentityOrder(List<Item> first, List<Item> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (first.get(index) != second.get(index)) {
                return false;
            }
        }
        return true;
    }

    private int countIdentity(List<Item> items, Item target) {
        int count = 0;
        for (Item item : items) {
            if (item == target) {
                count++;
            }
        }
        return count;
    }

    private static boolean removeIdentity(List<Item> items, Item target) {
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index) == target) {
                items.remove(index);
                return true;
            }
        }
        return false;
    }

    private static ItemStack[] cloneExactArray(ItemStack[] source) {
        Objects.requireNonNull(source, "source");
        ItemStack[] clone = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            clone[index] = cloneExact(source[index]);
        }
        return clone;
    }

    private static ItemStack cloneExact(ItemStack item) {
        return item == null ? null : item.clone();
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
