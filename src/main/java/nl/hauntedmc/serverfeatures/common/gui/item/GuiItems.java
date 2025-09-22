package nl.hauntedmc.serverfeatures.common.gui.item;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * Helpers for building consistent GUI ItemStacks.
 * Provides:
 * - Generic button(name, lore)
 * - Info paper(name, lore)
 * - Filler pane
 * - Common controls (back arrow, close barrier, locked/permission-gated)
 * - Empty GuiItem factory
 */
public final class GuiItems {
    private GuiItems() {}

    /** Generic button-style item with a display name and optional lore. */
    public static ItemStack button(Material type, Component name, Component... lore) {
        ItemStack is = new ItemStack(type);
        ItemMeta meta = is.getItemMeta();
        meta.displayName(name);
        if (lore != null && lore.length > 0) meta.lore(Arrays.asList(lore));
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_DYE
        );
        is.setItemMeta(meta);
        return is;
    }

    /** Paper info card with a name and optional lore. */
    public static ItemStack info(Component name, Component... lore) {
        return button(Material.PAPER, name, lore);
    }

    /** Neutral glass filler pane. */
    public static ItemStack filler() {
        return button(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
    }

    /** Standard back arrow item. */
    public static ItemStack backArrow() {
        return button(Material.ARROW, Component.text("Back"));
    }

    /** Standard close button (barrier). */
    public static ItemStack closeBarrier() {
        return button(Material.BARRIER, Component.text("Close"));
    }

    /** Barrier with a label used for permission-gated or locked items. */
    public static ItemStack locked(Component reason) {
        return button(Material.BARRIER, Component.text("Locked"), reason);
    }

    /** Empty gui item factory that renders AIR. */
    public static GuiItem empty() {
        return GuiItem.builder().factory(p -> new ItemStack(Material.AIR)).build();
    }
}
