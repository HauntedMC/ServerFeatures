package nl.hauntedmc.serverfeatures.features.invtools.service;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static nl.hauntedmc.serverfeatures.features.invtools.support.TestItemStacks.item;

class InvToolsServiceTest {

    @Test
    void onlineMutationRequiresTheClickedSlotToStillMatchTheRenderedSnapshot() {
        InventorySnapshot rendered = InventorySnapshot.empty()
                .withBackingSlot(InventoryKind.PLAYER, 12, item(Material.DIAMOND, 3));
        InventorySnapshot sameSlot = rendered
                .withBackingSlot(InventoryKind.PLAYER, 13, item(Material.EMERALD));
        InventorySnapshot changedSlot = rendered
                .withBackingSlot(InventoryKind.PLAYER, 12, item(Material.DIAMOND, 2));

        assertTrue(InvToolsService.onlineSlotMatches(
                rendered,
                sameSlot,
                InventoryKind.PLAYER,
                12
        ));
        assertFalse(InvToolsService.onlineSlotMatches(
                rendered,
                changedSlot,
                InventoryKind.PLAYER,
                12
        ));
    }
}
