package nl.hauntedmc.serverfeatures.features.autopickup.transfer;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static nl.hauntedmc.serverfeatures.features.invtools.support.TestItemStacks.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoPickupTransferPlannerTest {

    private final AutoPickupTransferPlanner planner = new AutoPickupTransferPlanner();

    @Test
    void mergesPartialStacksBeforeUsingEmptyStorageOrOffhand() {
        ItemStack[] storage = new ItemStack[36];
        storage[0] = item(Material.COBBLESTONE, 60);

        var plan = planner.plan(storage, null, List.of(item(Material.COBBLESTONE, 10)), 64);

        assertEquals(64, plan.finalStorage()[0].getAmount());
        assertEquals(6, plan.finalStorage()[1].getAmount());
        assertNull(plan.finalOffhand());
        assertEquals(10, plan.totalInserted());
        assertEquals(0, plan.totalRemaining());
        assertNull(plan.drops().getFirst().remainder());
        assertEquals(60, storage[0].getAmount(), "source storage must remain detached");
    }

    @Test
    void usesEmptyOffhandAfterStorageIsFull() {
        ItemStack[] storage = fullStorage(Material.STONE, 64);

        var plan = planner.plan(storage, null, List.of(item(Material.DIAMOND, 7)), 64);

        assertEquals(7, plan.finalOffhand().getAmount());
        assertTrue(plan.finalOffhand().isSimilar(item(Material.DIAMOND)));
        assertEquals(7, plan.totalInserted());
        assertEquals(0, plan.totalRemaining());
        assertNull(plan.drops().getFirst().remainder());
    }

    @Test
    void topsUpCompatibleOffhandAfterStorageIsFull() {
        ItemStack[] storage = fullStorage(Material.STONE, 64);
        ItemStack offhand = item(Material.DIAMOND, 60);

        var plan = planner.plan(storage, offhand, List.of(item(Material.DIAMOND, 10)), 64);

        assertEquals(64, plan.finalOffhand().getAmount());
        assertEquals(4, plan.totalInserted());
        assertEquals(6, plan.totalRemaining());
        assertEquals(6, plan.drops().getFirst().remainingAmount());
        assertEquals(60, offhand.getAmount(), "source offhand must remain detached");
    }

    @Test
    void storageCapacityIsUsedBeforeCompatibleOffhand() {
        ItemStack[] storage = fullStorage(Material.STONE, 64);
        storage[4] = item(Material.DIAMOND, 60);
        ItemStack offhand = item(Material.DIAMOND, 60);

        var plan = planner.plan(storage, offhand, List.of(item(Material.DIAMOND, 6)), 64);

        assertEquals(64, plan.finalStorage()[4].getAmount());
        assertEquals(62, plan.finalOffhand().getAmount());
        assertEquals(6, plan.totalInserted());
        assertEquals(0, plan.totalRemaining());
    }

    @Test
    void incompatibleOffhandIsNeverReplaced() {
        ItemStack[] storage = fullStorage(Material.STONE, 64);
        ItemStack offhand = item(Material.SHIELD, 1, 1);

        var plan = planner.plan(storage, offhand, List.of(item(Material.DIAMOND, 7)), 64);

        assertTrue(plan.finalOffhand().isSimilar(offhand));
        assertEquals(1, plan.finalOffhand().getAmount());
        assertEquals(0, plan.totalInserted());
        assertEquals(7, plan.totalRemaining());
        assertEquals(7, plan.drops().getFirst().remainder().getAmount());
        assertEquals(1, plan.remainingStacks());
    }

    @Test
    void leavesExactRemainderWhenStorageAndOffhandAreFull() {
        ItemStack[] storage = fullStorage(Material.STONE, 64);
        ItemStack offhand = item(Material.DIAMOND, 64);

        var plan = planner.plan(storage, offhand, List.of(item(Material.DIAMOND, 7)), 64);

        assertEquals(0, plan.totalInserted());
        assertEquals(7, plan.totalRemaining());
        assertEquals(7, plan.drops().getFirst().remainder().getAmount());
        assertEquals(1, plan.remainingStacks());
    }

    @Test
    void multipleDropsCompeteForStorageAndOffhandCapacityInOrder() {
        ItemStack[] storage = fullStorage(Material.STONE, 64);
        storage[4] = item(Material.DIAMOND, 60);
        ItemStack offhand = item(Material.DIAMOND, 62);

        var plan = planner.plan(
                storage,
                offhand,
                List.of(item(Material.DIAMOND, 3), item(Material.DIAMOND, 5)),
                64
        );

        assertEquals(3, plan.drops().get(0).insertedAmount());
        assertEquals(3, plan.drops().get(1).insertedAmount());
        assertEquals(2, plan.drops().get(1).remainingAmount());
        assertEquals(64, plan.finalStorage()[4].getAmount());
        assertEquals(64, plan.finalOffhand().getAmount());
        assertEquals(6, plan.totalInserted());
        assertEquals(2, plan.totalRemaining());
    }

    @Test
    void unstackableItemsUseSeparateStorageSlotsBeforeOffhand() {
        ItemStack[] storage = new ItemStack[36];

        var plan = planner.plan(
                storage,
                null,
                List.of(
                        item(Material.DIAMOND_PICKAXE, 1, 1),
                        item(Material.DIAMOND_PICKAXE, 1, 1)
                ),
                64
        );

        assertEquals(1, plan.finalStorage()[0].getAmount());
        assertEquals(1, plan.finalStorage()[1].getAmount());
        assertNull(plan.finalOffhand());
        assertEquals(2, plan.totalInserted());
        assertEquals(0, plan.totalRemaining());
    }

    @Test
    void randomizedPlansAlwaysConserveEveryDrop() {
        Random random = new Random(1847L);
        Material[] materials = {Material.STONE, Material.COBBLESTONE, Material.DIAMOND, Material.IRON_INGOT};

        for (int iteration = 0; iteration < 500; iteration++) {
            ItemStack[] storage = new ItemStack[36];
            for (int slot = 0; slot < storage.length; slot++) {
                if (random.nextBoolean()) {
                    Material material = materials[random.nextInt(materials.length)];
                    storage[slot] = item(material, 1 + random.nextInt(64));
                }
            }
            ItemStack offhand = null;
            if (random.nextBoolean()) {
                Material material = materials[random.nextInt(materials.length)];
                offhand = item(material, 1 + random.nextInt(64));
            }
            List<ItemStack> drops = new ArrayList<>();
            int originalTotal = 0;
            for (int drop = 0; drop < 1 + random.nextInt(8); drop++) {
                Material material = materials[random.nextInt(materials.length)];
                int amount = 1 + random.nextInt(64);
                drops.add(item(material, amount));
                originalTotal += amount;
            }

            var plan = planner.plan(storage, offhand, drops, 64);

            assertEquals(originalTotal, plan.totalInserted() + plan.totalRemaining());
            for (int index = 0; index < drops.size(); index++) {
                var result = plan.drops().get(index);
                assertEquals(
                        drops.get(index).getAmount(),
                        result.insertedAmount() + result.remainingAmount()
                );
            }
            assertTrue(plan.totalInserted() >= 0);
            assertTrue(plan.totalRemaining() >= 0);
        }
    }

    private static ItemStack[] fullStorage(Material material, int amount) {
        ItemStack[] storage = new ItemStack[36];
        for (int slot = 0; slot < storage.length; slot++) {
            storage[slot] = item(material, amount);
        }
        return storage;
    }
}
