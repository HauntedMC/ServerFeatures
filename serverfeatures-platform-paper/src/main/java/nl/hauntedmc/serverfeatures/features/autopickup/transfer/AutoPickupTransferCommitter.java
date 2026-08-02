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
            applyPlannedEventMutations(event, eligibleItems, plan);
            verifyCommittedState(
                    inventory,
                    event,
                    eligibleItems,
                    plan,
                    originalEventItems,
                    originalStacks,
                    plannedFinalStorage
            );
        } catch (RuntimeException commitFailure) {
            try {
                rollback(
                        inventory,
                        event,
                        eligibleItems,
                        plan,
                        originalStorage,
                        originalEventItems,
                        originalStacks,
                        plannedFinalStorage
                );
            } catch (RuntimeException rollbackFailure) {
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

    private void applyPlannedEventMutations(BlockDropItemEvent event,
                                            List<Item> eligibleItems,
                                            AutoPickupTransferPlanner.TransferPlan plan) {
        for (int index = 0; index < eligibleItems.size(); index++) {
            Item item = eligibleItems.get(index);
            ItemStack remainder = plan.drops().get(index).remainder();
            if (remainder == null) {
                if (!removeIdentity(event.getItems(), item)) {
                    throw new IllegalStateException("AutoPickup drop disappeared before commit");
                }
            } else {
                item.setItemStack(remainder);
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
        ExpectedEventState expected = expectedCommittedEventState(
                eligibleItems,
                plan,
                originalEventItems,
                originalStacks
        );
        verifyEventState(event, expected, "AutoPickup event state did not match the plan");
    }

    private void rollback(PlayerInventory inventory,
                          BlockDropItemEvent event,
                          List<Item> eligibleItems,
                          AutoPickupTransferPlanner.TransferPlan plan,
                          ItemStack[] originalStorage,
                          List<Item> originalEventItems,
                          Map<Item, ItemStack> originalStacks,
                          ItemStack[] plannedFinalStorage) {
        RuntimeException inventoryRestoreFailure = null;
        try {
            inventory.setStorageContents(cloneExactArray(originalStorage));
        } catch (RuntimeException exception) {
            inventoryRestoreFailure = exception;
        }

        ItemStack[] liveStorage;
        try {
            liveStorage = inventory.getStorageContents();
        } catch (RuntimeException exception) {
            if (inventoryRestoreFailure != null) {
                exception.addSuppressed(inventoryRestoreFailure);
            }
            throw new IllegalStateException(
                    "AutoPickup could not inspect inventory state during rollback; event state was left unchanged",
                    exception
            );
        }

        if (sameStorage(liveStorage, originalStorage)) {
            forceEventState(
                    event,
                    new ExpectedEventState(originalEventItems, originalStacks),
                    "AutoPickup original event state could not be restored"
            );
            if (!sameStorage(inventory.getStorageContents(), originalStorage)) {
                throw new IllegalStateException("AutoPickup inventory changed after successful rollback");
            }
            return;
        }

        if (sameStorage(liveStorage, plannedFinalStorage)) {
            ExpectedEventState committedState = expectedCommittedEventState(
                    eligibleItems,
                    plan,
                    originalEventItems,
                    originalStacks
            );
            forceEventState(
                    event,
                    committedState,
                    "AutoPickup could not finish the event side after inventory rollback failed"
            );
            IllegalStateException fallback = new IllegalStateException(
                    "AutoPickup inventory rollback failed; the matching event commit was completed to preserve conservation"
            );
            if (inventoryRestoreFailure != null) {
                fallback.addSuppressed(inventoryRestoreFailure);
            }
            throw fallback;
        }

        IllegalStateException unknownState = new IllegalStateException(
                "AutoPickup inventory is neither the original nor planned state; event state was left unchanged"
        );
        if (inventoryRestoreFailure != null) {
            unknownState.addSuppressed(inventoryRestoreFailure);
        }
        throw unknownState;
    }

    private ExpectedEventState expectedCommittedEventState(List<Item> eligibleItems,
                                                           AutoPickupTransferPlanner.TransferPlan plan,
                                                           List<Item> originalEventItems,
                                                           Map<Item, ItemStack> originalStacks) {
        List<Item> expectedItems = new ArrayList<>(originalEventItems);
        Map<Item, ItemStack> expectedStacks = new IdentityHashMap<>(originalStacks);
        for (int index = 0; index < eligibleItems.size(); index++) {
            Item item = eligibleItems.get(index);
            ItemStack remainder = plan.drops().get(index).remainder();
            if (remainder == null) {
                if (!removeIdentity(expectedItems, item)) {
                    throw new IllegalStateException("Eligible AutoPickup item was absent from the original event");
                }
                expectedStacks.remove(item);
            } else {
                expectedStacks.put(item, remainder);
            }
        }
        return new ExpectedEventState(expectedItems, expectedStacks);
    }

    private void forceEventState(BlockDropItemEvent event,
                                 ExpectedEventState expected,
                                 String failureMessage) {
        RuntimeException operationFailure = null;
        try {
            event.getItems().clear();
            event.getItems().addAll(expected.items());
        } catch (RuntimeException exception) {
            operationFailure = appendFailure(operationFailure, exception);
        }
        for (Item item : expected.items()) {
            try {
                item.setItemStack(cloneExact(expected.stacks().get(item)));
            } catch (RuntimeException exception) {
                operationFailure = appendFailure(operationFailure, exception);
            }
        }

        try {
            verifyEventState(event, expected, failureMessage);
        } catch (RuntimeException verificationFailure) {
            if (operationFailure != null) {
                verificationFailure.addSuppressed(operationFailure);
            }
            throw verificationFailure;
        }
    }

    private void verifyEventState(BlockDropItemEvent event,
                                  ExpectedEventState expected,
                                  String failureMessage) {
        if (!sameIdentityOrder(event.getItems(), expected.items())) {
            throw new IllegalStateException(failureMessage + ": membership or order mismatch");
        }
        verifyStacks(expected.items(), expected.stacks(), failureMessage + ": stack mismatch");
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

    private static RuntimeException appendFailure(RuntimeException current, RuntimeException additional) {
        if (current == null) {
            return additional;
        }
        current.addSuppressed(additional);
        return current;
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

    private record ExpectedEventState(List<Item> items, Map<Item, ItemStack> stacks) {
        private ExpectedEventState {
            items = List.copyOf(items);
            Map<Item, ItemStack> detachedStacks = new IdentityHashMap<>();
            stacks.forEach((item, stack) -> detachedStacks.put(item, cloneExact(stack)));
            stacks = detachedStacks;
        }
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
