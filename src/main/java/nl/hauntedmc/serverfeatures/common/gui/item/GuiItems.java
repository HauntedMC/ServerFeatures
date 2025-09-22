// File: nl/hauntedmc/serverfeatures/common/gui/item/GuiItems.java
package nl.hauntedmc.serverfeatures.common.gui.item;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/** Small helpers for building consistent-looking GUI items. */
public final class GuiItems {
    private GuiItems() {}

    public static ItemStack button(Material type, Component name, Component... lore) {
        ItemStack is = new ItemStack(type);
        ItemMeta meta = is.getItemMeta();
        meta.displayName(name);
        if (lore != null && lore.length > 0) meta.lore(Arrays.asList(lore));
        // Safe, broadly supported flags:
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_DYE
        );
        is.setItemMeta(meta);
        return is;
    }

    public static ItemStack info(Component name, Component... lore) {
        return button(Material.PAPER, name, lore);
    }

    public static ItemStack filler() {
        return button(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
    }

    public static GuiItem empty() {
        return GuiItem.builder().factory(p -> new ItemStack(Material.AIR)).build();
    }
}
