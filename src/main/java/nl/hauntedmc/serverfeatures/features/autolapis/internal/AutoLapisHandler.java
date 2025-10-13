package nl.hauntedmc.serverfeatures.features.autolapis.internal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import nl.hauntedmc.serverfeatures.features.autolapis.AutoLapis;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class AutoLapisHandler {

    public static final String PERM_USE = "serverfeatures.feature.autolapis.use";

    private final NamespacedKey markerKey;
    private final int stackSize;

    public AutoLapisHandler(AutoLapis feature) {
        this.stackSize = feature.getConfigHandler().node("stack_size").as(Integer.class, 1);
        this.markerKey = new NamespacedKey(feature.getPlugin(), "autolapis_marker");
    }

    public boolean eligible(HumanEntity viewer) {
        return viewer instanceof Player p && p.hasPermission(PERM_USE);
    }

    public ItemStack makeMarkerLapis() {
        ItemStack lapis = new ItemStack(Material.LAPIS_LAZULI, stackSize);
        ItemMeta meta = lapis.getItemMeta();
        meta.displayName(Component.text("Lapis (Rank Perk)").decoration(TextDecoration.ITALIC, false).color(NamedTextColor.AQUA));
        // Mark it so we can detect & block moves
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        lapis.setItemMeta(meta);
        return lapis;
    }

    public boolean isMarker(ItemStack stack) {
        if (stack == null || stack.getType() != Material.LAPIS_LAZULI || !stack.hasItemMeta()) return false;
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        return pdc.has(markerKey, PersistentDataType.BYTE);
    }

    /**
     * Ensure the enchanting inventory has our marker lapis in its secondary slot.
     */
    public void ensureMarker(EnchantingInventory inv) {
        ItemStack sec = inv.getSecondary();
        if (!isMarker(sec)) {
            inv.setSecondary(makeMarkerLapis());
        } else if (sec.getAmount() < stackSize) {
            // top up if reduced by an enchant
            sec.setAmount(stackSize);
            inv.setSecondary(sec);
        }
    }

    /**
     * Remove marker lapis from enchanting inventory (so it never drops on close).
     */
    public void clearMarker(EnchantingInventory inv) {
        ItemStack sec = inv.getSecondary();
        if (isMarker(sec)) {
            inv.setSecondary(null);
        }
    }
}
