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
import static org.junit.jupiter.api.Assertions.assertNull;
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
        InventoryHarness inventory = inventory(new ItemStack[36], null);
        ItemHarness dropped = droppedItem(item(Material.DIAMOND, 3));
        List<Item> eventItems = new ArrayList<>(List.of(dropped.entity()));
        BlockDropItemEvent event = event(eventItems);
        var plan = plan(inventory, dropped);

        committer.commit(inventory.inventory(), event, List.of(dropped.entity()), plan);

        assertEquals(3, inventory.contents().get()[0].getAmount());
        assertTrue(inventory.contents().get()[0].isSimilar(item(Material.DIAMOND)));
        assertNull(inventory.offhand().get());
        assertEquals(0, eventItems.size());
    }

    @Test
    void completeInsertionUsesOffhandWhenStorageIsFull() {
        InventoryHarness inventory = inventory(fullStorage(Material.STONE, 64), null);
        ItemHarness dropped = droppedItem(item(Material.DIAMOND, 3));
        List<Item> eventItems = new ArrayList<>(List.of(dropped.entity()));
        BlockDropItemEvent event = event(eventItems);
        var plan = plan(inventory, dropped);

        committer.commit(inventory.inventory(), event, List.of(dropped.entity()), plan);

        assertEquals(3, inventory.offhand().get().getAmount());
        assertTrue(inventory.offhand().get().isSimilar(item(Material.DIAMOND)));
        assertEquals(0, eventItems.size());
    }

    @Test
    void partialInsertionRetainsTheSameEntityWithTheExactRemainder() {
        ItemStack[] initial = fullStorage(Material.STONE, 64);
        initial[4] = item(Material.DIAMOND, 63);
        InventoryHarness inventory = inventory(initial, item(Material.DIAMOND, 64));
        ItemHarness dropped = droppedItem(item(Material.DIAMOND, 3));
        List<Item> eventItems = new ArrayList<>(List.of(dropped.entity()));
        BlockDropItemEvent event = event(eventItems);
        var plan = plan(inventory, dropped);

        committer.commit(inventory.inventory(), event, List.of(dropped.entity()), plan);

        assertEquals(64, inventory.contents().get()[4].getAmount());
        assertEquals(64, inventory.offhand().get().getAmount());
        assertEquals(1, eventItems.size());
        assertSame(dropped.entity(), eventItems.getFirst());
        assertEquals(2, dropped.stack().get().getAmount());
    }

    @Test
    void mutationFailureRestoresInventoryOffhandEventMembershipAndOriginalStack() {
        InventoryHarness inventory = inventory(fullStorage(Material.STONE, 64), null);
        ItemHarness dropped = droppedItem(item(Material.DIAMOND, 3));
        FailOnceRemoveList eventItems = new FailOnceRemoveList();
        eventItems.add(dropped.entity());
        BlockDropItemEvent event = event(eventItems);
        var plan = plan(inventory, dropped);

        AutoPickupCommitException failure = assertThrows(
                AutoPickupCommitException.class,
                () -> committer.commit(inventory.inventory(), event, List.of(dropped.entity()), plan)
        );

        assertFalse(failure.rollbackFailed());
        assertEquals(36 * 64, countItems(inventory.contents().get()));
        assertNull(inventory.offhand().get());
        assertEquals(1, eventItems.size());
        assertSame(dropped.entity(), eventItems.getFirst());
        assertEquals(3, dropped.stack().get().getAmount());
    }

    @Test
    void rollbackCompletesEventCommitWhenInventoryRestoreFails() {
        InventoryHarness inventory = inventory(new ItemStack[36], null, 2, -1);
        ItemHarness dropped = droppedItem(item(Material.DIAMOND, 3));
        FailOnceRemoveList eventItems = new FailOnceRemoveList();
        eventItems.add(dropped.entity());
        BlockDropItemEvent event = event(eventItems);
        var plan = plan(inventory, dropped);

        AutoPickupCommitException failure = assertThrows(
                AutoPickupCommitException.class,
                () -> committer.commit(inventory.inventory(), event, List.of(dropped.entity()), plan)
        );

        assertTrue(failure.rollbackFailed());
        assertEquals(3, countItems(inventory.contents().get()));
        assertNull(inventory.offhand().get());
        assertEquals(0, eventItems.size());
    }

    @Test
    void storageChangeAfterPlanningRejectsBeforeAnyMutation() {
        InventoryHarness inventory = inventory(new ItemStack[36], null);
        ItemHarness dropped = droppedItem(item(Material.DIAMOND, 3));
        List<Item> eventItems = new ArrayList<>(List.of(dropped.entity()));
        BlockDropItemEvent event = event(eventItems);
        var plan = plan(inventory, dropped);
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
        assertNull(inventory.offhand().get());
        assertEquals(1, eventItems.size());
        assertEquals(3, dropped.stack().get().getAmount());
    }

    @Test
    void offhandChangeAfterPlanningRejectsBeforeAnyMutation() {
        InventoryHarness inventory = inventory(fullStorage(Material.STONE, 64), null);
        ItemHarness dropped = droppedItem(item(Material.DIAMOND, 3));
        List<Item> eventItems = new ArrayList<>(List.of(dropped.entity()));
        BlockDropItemEvent event = event(eventItems);
        var plan = plan(inventory, dropped);
        inventory.offhand().set(item(Material.SHIELD, 1, 1));

        AutoPickupCommitException failure = assertThrows(
                AutoPickupCommitException.class,
                () -> committer.commit(inventory.inventory(), event, List.of(dropped.entity()), plan)
        );

        assertFalse(failure.rollbackFailed());
        assertTrue(inventory.offhand().get().isSimilar(item(Material.SHIELD, 1, 1)));
        assertEquals(1, eventItems.size());
        assertEquals(3, dropped.stack().get().getAmount());
    }

    @Test
    void silentRemainderCorruptionIsDetectedAndRolledBack() {
        ItemStack[] initial = fullStorage(Material.STONE, 64);
        initial[4] = item(Material.DIAMOND, 63);
        InventoryHarness inventory = inventory(initial, item(Material.DIAMOND, 64));
        ItemHarness dropped = firstStackMutationCorrupted(item(Material.DIAMOND, 3));
        List<Item> eventItems = new ArrayList<>(List.of(dropped.entity()));
        BlockDropItemEvent event = event(eventItems);
        var plan = plan(inventory, dropped);

        AutoPickupCommitException failure = assertThrows(
                AutoPickupCommitException.class,
                () -> committer.commit(inventory.inventory(), event, List.of(dropped.entity()), plan)
        );

        assertFalse(failure.rollbackFailed());
        assertEquals(63, inventory.contents().get()[4].getAmount());
        assertEquals(64, inventory.offhand().get().getAmount());
        assertEquals(1, eventItems.size());
        assertSame(dropped.entity(), eventItems.getFirst());
        assertEquals(3, dropped.stack().get().getAmount());
    }

    private AutoPickupTransferPlanner.TransferPlan plan(InventoryHarness inventory, ItemHarness dropped) {
        return planner.plan(
                inventory.inventory().getStorageContents(),
                inventory.inventory().getItemInOffHand(),
                List.of(dropped.entity().getItemStack()),
                64
        );
    }

    private static InventoryHarness inventory(ItemStack[] initial, ItemStack initialOffhand) {
        return inventory(initial, initialOffhand, -1, -1);
    }

    private static InventoryHarness inventory(ItemStack[] initial,
                                              ItemStack initialOffhand,
                                              int failOnStorageSetCall,
                                              int failOnOffhandSetCall) {
        PlayerInventory inventory = mock(PlayerInventory.class);
        AtomicReference<ItemStack[]> contents = new AtomicReference<>(
                AutoPickupTransferPlanner.cloneArray(initial)
        );
        AtomicReference<ItemStack> offhand = new AtomicReference<>(
                AutoPickupTransferPlanner.cloneOrNull(initialOffhand)
        );
        AtomicInteger storageSetCalls = new AtomicInteger();
        AtomicInteger offhandSetCalls = new AtomicInteger();
        when(inventory.getStorageContents()).thenAnswer(unused ->
                AutoPickupTransferPlanner.cloneArray(contents.get())
        );
        when(inventory.getItemInOffHand()).thenAnswer(unused ->
                AutoPickupTransferPlanner.cloneOrNull(offhand.get())
        );
        doAnswer(invocation -> {
            int call = storageSetCalls.incrementAndGet();
            if (call == failOnStorageSetCall) {
                throw new IllegalStateException("simulated storage mutation failure");
            }
            ItemStack[] replacement = invocation.getArgument(0);
            contents.set(AutoPickupTransferPlanner.cloneArray(replacement));
            return null;
        }).when(inventory).setStorageContents(any(ItemStack[].class));
        doAnswer(invocation -> {
            int call = offhandSetCalls.incrementAndGet();
            if (call == failOnOffhandSetCall) {
                throw new IllegalStateException("simulated offhand mutation failure");
            }
            ItemStack replacement = invocation.getArgument(0);
            offhand.set(AutoPickupTransferPlanner.cloneOrNull(replacement));
            return null;
        }).when(inventory).setItemInOffHand(any());
        return new InventoryHarness(inventory, contents, offhand);
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

    private record InventoryHarness(
            PlayerInventory inventory,
            AtomicReference<ItemStack[]> contents,
            AtomicReference<ItemStack> offhand
    ) {
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
