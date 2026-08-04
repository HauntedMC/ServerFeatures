package nl.hauntedmc.serverfeatures.features.graveyard.claim;

import nl.hauntedmc.serverfeatures.features.graveyard.capture.PlayerInventoryState;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GraveItemEntry;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GravePayload;
import nl.hauntedmc.serverfeatures.features.graveyard.persistence.GravePayloadCodec;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static nl.hauntedmc.serverfeatures.features.graveyard.GraveyardTestItemStacks.binaryCodec;
import static nl.hauntedmc.serverfeatures.features.graveyard.GraveyardTestItemStacks.stack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraveClaimPlannerTest {
    private final GravePayloadCodec codec = new GravePayloadCodec(
            64,
            1_000_000,
            4_000_000,
            binaryCodec()
    );

    @Test
    void restoresPreferredSlotThenMergesWithoutCreatingOverstackedItems() throws Exception {
        PlayerInventoryState inventory = new PlayerInventoryState();
        inventory.set(1, stack(Material.DIAMOND, 60));
        GraveItemEntry entry = codec.createEntry(UUID.randomUUID(), 0, stack(Material.DIAMOND, 70));

        ClaimTransferPlan plan = new GraveClaimPlanner(codec, true).plan(
                inventory,
                new GravePayload(0L, List.of(entry), 10)
        );

        assertEquals(64, plan.resultingInventory().get(0).getAmount());
        assertEquals(64, plan.resultingInventory().get(1).getAmount());
        assertEquals(2, plan.resultingInventory().get(2).getAmount());
        assertTrue(plan.remainingPayload().isEmpty());
        assertEquals(10, plan.transferredExperience());
    }

    @Test
    void keepsOverflowInTheGraveAndAllOrNothingModeLeavesInventoryUntouched() throws Exception {
        PlayerInventoryState full = new PlayerInventoryState();
        for (int slot = 0; slot < 36; slot++) {
            full.set(slot, stack(Material.COBBLESTONE, 64));
        }
        GraveItemEntry entry = codec.createEntry(UUID.randomUUID(), 0, stack(Material.DIAMOND, 5));
        GravePayload payload = new GravePayload(0L, List.of(entry), 0);

        ClaimTransferPlan partial = new GraveClaimPlanner(codec, true).plan(full, payload);
        assertFalse(partial.changed());
        assertEquals(5, codec.decodeItem(partial.remainingPayload().entries().getFirst()).getAmount());

        ClaimTransferPlan atomic = new GraveClaimPlanner(codec, false).plan(full, payload);
        assertFalse(atomic.changed());
        assertEquals(5, codec.decodeItem(atomic.remainingPayload().entries().getFirst()).getAmount());
    }
}
