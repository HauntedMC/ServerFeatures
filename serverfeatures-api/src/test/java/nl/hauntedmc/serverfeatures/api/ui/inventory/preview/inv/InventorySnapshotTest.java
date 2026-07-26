package nl.hauntedmc.serverfeatures.api.ui.inventory.preview.inv;

import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class InventorySnapshotTest {

    @Test
    void snapshotCapturesExpectedLayoutWhenInventoryIsEmpty() {
        PlayerInventory inventory = InterfaceProxy.of(PlayerInventory.class, Map.of(
                "getItem", args -> null,
                "getHelmet", args -> null,
                "getChestplate", args -> null,
                "getLeggings", args -> null,
                "getBoots", args -> null,
                "getItemInOffHand", args -> null
        ));

        Player player = InterfaceProxy.of(Player.class, Map.of(
                "getInventory", args -> inventory,
                "getName", args -> "Remy"
        ));

        InventorySnapshot snapshot = InventorySnapshot.from(player);

        assertEquals("Remy", snapshot.inventoryOwner());
        assertEquals(27, snapshot.main().length);
        assertEquals(9, snapshot.hotbar().length);
        assertNull(snapshot.main()[0]);
        assertNull(snapshot.hotbar()[0]);
        assertNull(snapshot.helmet());
        assertNull(snapshot.chestplate());
        assertNull(snapshot.leggings());
        assertNull(snapshot.boots());
        assertNull(snapshot.offhand());
    }

    @Test
    void accessorsReturnDefensiveCopies() {
        PlayerInventory inventory = InterfaceProxy.of(PlayerInventory.class, Map.of(
                "getItem", args -> null,
                "getHelmet", args -> null,
                "getChestplate", args -> null,
                "getLeggings", args -> null,
                "getBoots", args -> null,
                "getItemInOffHand", args -> null
        ));

        Player player = InterfaceProxy.of(Player.class, Map.of(
                "getInventory", args -> inventory,
                "getName", args -> "Remy"
        ));

        InventorySnapshot snapshot = InventorySnapshot.from(player);
        ItemStack[] first = snapshot.main();
        ItemStack[] second = snapshot.main();

        assertNotSame(first, second);
        first[0] = null;
        assertNull(snapshot.main()[0]);
        assertNull(snapshot.offhand());
    }
}
