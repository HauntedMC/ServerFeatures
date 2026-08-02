package nl.hauntedmc.serverfeatures.features.autopickup.transfer;

import nl.hauntedmc.serverfeatures.features.autopickup.transfer.AutoPickupTransferCommitter.AutoPickupCommitException;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static nl.hauntedmc.serverfeatures.features.invtools.support.TestItemStacks.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutoPickupTransferCommitterTest {

    private final AutoPickupTransferPlanner planner = new AutoPickupTransferPlanner();
    private final AutoPickupTransferCommitter committer = new AutoPickupTransferCommitter();

    @Test
    void completeInsertionRemovesTheOriginalEventItem() {
        InventoryHarness inventory = inventory(new ItemStack[36]);
        ItemHarness dropped = droppedItem(item(Material.DIAMOND, 3));
        List<Item> eventItems = new ArrayList<>(List.of(dropped.entity()));
        BlockDropItemEvent event = event(eventItems);
        var plan = planner.plan(
                inventory.inventory().getStorageContents(),
                List.of(dropped.entity().getItemStack()),
                64
        );

        committer.commit(inventory.inventory(), event, List.of(dropped.entity()), plan);

        assertEquals(3, inventory.contents().get()[0].getAmount());
        assertTrue(inventory.contents().get()[0].isSimilar(item(Material.DIAMOND)));
        assertEquals(0, eventItems.size());
    }

    @Test
    void partialInsertionRetainsTheSameEntityWithTheExactRemainder() {
        ItemStack[] initial = fullStorage(Material.STONE, 64);
        initial[4] = item(Material.DIAMOND, 63);
        InventoryHarness inventory = inventory(initial);
        ItemHarness dropped = droppedItem(item(Material.DIAMOND, 3));
        List<Item> eventItems = new ArrayList<>(List.of(dropped.entity()));
        BlockDropItemEvent event = event(eventItems);
        var plan = planner.plan(
                inventory.inventory().getStorageContents(),
                List.of(dropped.entity().getItemStack()),
                64
        );

        committer.commit(inventory.inventory(), event, List.of(dropped.entity()), plan);

        assertEquals(64, inventory.contents().get()[4].getAmount());
        assertEquals(1, eventItems.size());
        assertSame(dropped.entity(), eventItems.getFirst());
        assertEquals(2, dropped.stack().get().getAmount());
    }

    @Test
    void mutationFailureRestoresInventoryEventMembershipAndOriginalStack() {
        InventoryHarness inventory = inventory(new ItemStack[36]);
        ItemHarness dropped = droppedItem(item(Material.DIAMOND, 3));
        FailOnceRemoveList eventItems = new FailOnceRemoveList();
        eventItems.add(dropped.entity());
        BlockDropItemEvent event = event(eventItems);
        var plan = planner.plan(
                inventory.inventory().getStorageContents(),
                List.of(dropped.entity().getItemStack()),
                64
        );

        AutoPickupCommitException failure = assertThrows(
                AutoPickupCommitException.class,
                () -> committer.commit(inventory.inventory(), event, List.of(dropped.entity()), plan)
        );

        assertFalse(failure.rollbackFailed());
        assertEquals(0, countItems(inventory.contents().get()));
        assertEquals(1, eventItems.size());
        assertSame(dropped.entity(), eventItems.getFirst());
        assertEquals(3, dropped.stack().get().getAmount());
    }

    @Test
    void rollbackCompletesEventCommitWhenInventoryRestoreFails() {
        InventoryHarness inventory = inventory(new ItemStack[36], 2);
        ItemHarness dropped = droppedItem(item(Material.DIAMOND, 3));
        FailOnceRemoveList eventItems = new FailOnceRemoveList();
        eventItems.add(dropped.entity());
        BlockDropItemEvent event = event(eventItems);
        var plan = planner.plan(
                inventory.inventory().getStorageContents(),
                List.of(dropped.entity().getItemStack()),
                64
        );

        AutoPickupCommitException failure = assertThrows(
                AutoPickupCommitException.class,
                () -> committer.commit(inventory.inventory(), event, List.of(dropped.entity()), plan)
        );

        assertTrue(failure.rollbackFailed());
        assertEquals(3, countItems(inventory.contents().get()));
        assertEquals(0, eventItems.size());
    }

    @Test
    void storageChangeAfterPlanningRejectsBeforeAnyMutation() {
        InventoryHarness inventory = inventory(new ItemStack[36]);
        ItemHarness dropped = droppedItem(item(Material.DIAMOND, 3));
        List<Item> eventItems = new ArrayList<>(List.of(dropped.entity()));
        BlockDropItemEvent event = event(eventItems);
        var plan = planner.plan(
                inventory.inventory().getStorageContents(),
                List.of(dropped.entity().getItemStack()),
                64
        );
        ItemStack[] changedStorage = new ItemStack[36];
        changedStorage[0] = item(Material.STONE, 1);
        inventory.contents().set(changedStorage);

        AutoPickupCommitException failure = assertThrows(
                AutoPickupCommitException.class,
                () -> committer.commit(inventory.inventory(), event, List.of(dropped.entity()), plan)
        );

        assertFalse(failure.rollbackFailed());
        assertEquals(1, inventory.contents().get()[0].getAmount());
        assertTrue(inventory.contents().get()[0].isSimilar(item(Material.STONE)));
        assertEquals(1, eventItems.size());
        assertEquals(3, dropped.stack().get().getAmount());
    }

    @Test
    void silentRemainderCorruptionIsDetectedAndRolledBack() {
        ItemStack[] initial = fullStorage(Material.STONE, 64);
        initial[4] = item(Material.DIAMOND, 63);
        InventoryHarness inventory = inventory(initial);
        ItemHarness dropped = firstStackMutationCorrupted(item(Material.DIAMOND, 3));
        List<Item> eventItems = new ArrayList<>(List.of(dropped.entity()));
        BlockDropItemEvent event = event(eventItems);
        var plan = planner.plan(
                inventory.inventory().getStorageContents(),
                List.of(dropped.entity().getItemStack()),
                64
        );

        AutoPickupCommitException failure = assertThrows(
                AutoPickupCommitException.class,
                () -> committer.commit(inventory.inventory(), event, List.of(dropped.entity()), plan)
        );

        assertFalse(failure.rollbackFailed());
        assertEquals(63, inventory.contents().get()[4].getAmount());
        assertEquals(1, eventItems.size());
        assertSame(dropped.entity(), eventItems.getFirst());
        assertEquals(3, dropped.stack().get().getAmount());
    }

    private static InventoryHarness inventory(ItemStack[] initial) {
        return inventory(initial, -1);
    }

    private static InventoryHarness inventory(ItemStack[] initial, int failOnSetCall) {
        PlayerInventory inventory = mock(PlayerInventory.class);
        AtomicReference<ItemStack[]> contents = new AtomicReference<>(
                AutoPickupTransferPlanner.cloneArray(initial)
        );
        AtomicInteger setCalls = new AtomicInteger();
        when(inventory.getStorageContents()).thenAnswer(unused ->
                AutoPickupTransferPlanner.cloneArray(contents.get())
        );
        doAnswer(invocation -> {
            int call = setCalls.incrementAndGet();
            if (call == failOnSetCall) {
                throw new IllegalStateException("simulated inventory mutation failure");
            }
            ItemStack[] replacement = invocation.getArgument(0);
            contents.set(AutoPickupTransferPlanner.cloneArray(replacement));
            return null;
        }).when(inventory).setStorageContents(any(ItemStack[].class));
        return new InventoryHarness(inventory, contents);
    }

    private static ItemHarness droppedItem(ItemStack initial) {
        return droppedItem(initial, false);
    }

    private static ItemHarness firstStackMutationCorrupted(ItemStack initial) {
        return droppedItem(initial, true);
    }

    private static ItemHarness droppedItem(ItemStack initial, boolean corruptFirstMutation) {
        Item entity = mock(Item.class);
        AtomicReference<ItemStack> stack = new AtomicReference<>(initial.clone());
        AtomicBoolean corrupt = new AtomicBoolean(corruptFirstMutation);
        when(entity.getItemStack()).thenAnswer(unused -> stack.get().clone());
        doAnswer(invocation -> {
            ItemStack replacement = ((ItemStack) invocation.getArgument(0)).clone();
            if (corrupt.compareAndSet(true, false)) {
                replacement.setAmount(replacement.getAmount() + 1);
            }
            stack.set(replacement);
            return null;
        }).when(entity).setItemStack(any(ItemStack.class));
        return new ItemHarness(entity, stack);
    }

    private static BlockDropItemEvent event(List<Item> items) {
        BlockDropItemEvent event = mock(BlockDropItemEvent.class);
        when(event.getItems()).thenReturn(items);
        return event;
    }

    private static ItemStack[] fullStorage(Material material, int amount) {
        ItemStack[] storage = new ItemStack[36];
        for (int slot = 0; slot < storage.length; slot++) {
            storage[slot] = item(material, amount);
        }
        return storage;
    }

    private static int countItems(ItemStack[] storage) {
        int total = 0;
        for (ItemStack item : storage) {
            if (item != null) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private record InventoryHarness(PlayerInventory inventory, AtomicReference<ItemStack[]> contents) {
    }

    private record ItemHarness(Item entity, AtomicReference<ItemStack> stack) {
    }

    private static final class FailOnceRemoveList extends ArrayList<Item> {
        private static final long serialVersionUID = 1L;

        private boolean fail = true;

        @Override
        public Item remove(int index) {
            if (fail) {
                fail = false;
                throw new IllegalStateException("simulated event-list mutation failure");
            }
            return super.remove(index);
        }
    }
}
