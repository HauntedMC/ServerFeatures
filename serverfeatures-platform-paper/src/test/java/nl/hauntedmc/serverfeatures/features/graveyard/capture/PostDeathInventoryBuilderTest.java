package nl.hauntedmc.serverfeatures.features.graveyard.capture;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static nl.hauntedmc.serverfeatures.features.graveyard.GraveyardTestItemStacks.stack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostDeathInventoryBuilderTest {
    @Test
    void retainedStacksAreSplitSafelyAcrossStorageSlots() {
        PlayerInventoryState original = new PlayerInventoryState();
        original.set(5, stack(Material.DIAMOND, 64));
        DeathInventorySnapshot snapshot = new DeathInventorySnapshot(UUID.randomUUID(), original, 0, null, 1L);

        PlayerInventoryState result = new PostDeathInventoryBuilder().build(
                snapshot,
                List.of(stack(Material.DIAMOND, 70))
        );

        assertEquals(64, result.get(5).getAmount());
        assertEquals(6, result.get(0).getAmount());
    }

    @Test
    void impossibleRetainedSetFailsClosedInsteadOfDroppingItems() {
        PlayerInventoryState original = new PlayerInventoryState();
        DeathInventorySnapshot snapshot = new DeathInventorySnapshot(UUID.randomUUID(), original, 0, null, 1L);
        List<ItemStack> retained = java.util.stream.IntStream.range(0, 37)
                .mapToObj(index -> stack(Material.DIAMOND_SWORD, 1))
                .toList();

        assertThrows(
                IllegalStateException.class,
                () -> new PostDeathInventoryBuilder().build(snapshot, retained)
        );
    }
}
