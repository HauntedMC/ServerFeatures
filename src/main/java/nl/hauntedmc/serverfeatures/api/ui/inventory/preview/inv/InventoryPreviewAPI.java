package nl.hauntedmc.serverfeatures.api.ui.inventory.preview.inv;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;


/**
 * Opens a clean, labeled 6x9 chest GUI rendering:
 * - 3 rows: main inventory (27)
 * - separator row
 * - hotbar (9)
 * - top row: armor (helmet -> boots) + offhand, with subtle labels/separators
 */
public final class InventoryPreviewAPI {

    private InventoryPreviewAPI() { }

    public static void openInventoryPreview(Plugin plugin, Player viewer, InventorySnapshot snapshot, Component title) {
        if (plugin == null || viewer == null || snapshot == null) return;

        final Inventory inv = Bukkit.createInventory(new InventoryPreviewHolder(snapshot), 6 * 9, title);

        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta();
        m.displayName(Component.text(" "));
        pane.setItemMeta(m);


        // Armor items
        inv.setItem(slot(0, 0), pane);
        inv.setItem(slot(0, 1), pane);
        inv.setItem(slot(0, 2), safe(snapshot.helmet()));
        inv.setItem(slot(0, 3), safe(snapshot.chestplate()));
        inv.setItem(slot(0, 4), safe(snapshot.leggings()));
        inv.setItem(slot(0, 5), safe(snapshot.boots()));
        inv.setItem(slot(0, 6), pane);
        inv.setItem(slot(0, 7), safe(snapshot.offhand()));
        inv.setItem(slot(0, 8), pane);

        for (int i = 0; i < 9; i++) {
            int slot = 9 + i;
            inv.setItem(slot, pane);
        }

        // Main inventory
        ItemStack[] main = snapshot.main();
        for (int i = 0; i < 27; i++) {
            int chestSlot = 18 + i;
            inv.setItem(chestSlot, safe(main[i]));
        }

        // Hotbar
        ItemStack[] hotbar = snapshot.hotbar();
        for (int i = 0; i < 9; i++) {
            inv.setItem(45 + i, safe(hotbar[i]));
        }

        // Open on main thread
        Bukkit.getScheduler().runTask(plugin, () -> viewer.openInventory(inv));
    }

    private static int slot(int row, int col) {
        return row * 9 + col;
    }

    private static ItemStack safe(ItemStack it) {
        return (it == null || it.getType().isAir()) ? null : it;
    }

}
