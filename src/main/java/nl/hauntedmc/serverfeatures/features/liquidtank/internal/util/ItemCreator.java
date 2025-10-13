package nl.hauntedmc.serverfeatures.features.liquidtank.internal.util;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.serverfeatures.api.util.text.format.TextFormatter;
import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class ItemCreator {

    private ItemCreator() {}

    // PDC keys (item-only; no blockstate needed)
    private static NamespacedKey keyKind(LiquidTank feature)   { return new NamespacedKey(feature.getPlugin(), "lt_kind"); }
    private static NamespacedKey keyVer(LiquidTank feature)    { return new NamespacedKey(feature.getPlugin(), "lt_ver"); }

    /**
     * Create a legit Liquid Tank item (HOPPER) with display name and PDC marker.
     */
    public static ItemStack createTankItem(LiquidTank feature, int amount) {
        ItemStack item = new ItemStack(Material.HOPPER, amount);
        ItemMeta meta = item.getItemMeta();

        // Use the same configured name you show elsewhere
        String raw = String.valueOf(feature.getTankManager().getItemName());
        Component display = ComponentFormatter
                .deserialize(raw)
                .expect(TextFormatter.InputFormat.MIXED_INPUT)
                .features(ComponentFormatter.Feature.COLORS)
                .toComponent();
        meta.displayName(display);

        // Stamp PDC so we can trust this item on placement
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyKind(feature), PersistentDataType.STRING, "liquid_tank");
        pdc.set(keyVer(feature),  PersistentDataType.INTEGER, 1);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Validate that the given stack is a legit Liquid Tank item created by us.
     */
    public static boolean isLiquidTankItem(LiquidTank feature, ItemStack stack) {
        if (stack == null || stack.getType() != Material.HOPPER) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String kind = pdc.get(keyKind(feature), PersistentDataType.STRING);
        Integer ver = pdc.get(keyVer(feature), PersistentDataType.INTEGER);

        return "liquid_tank".equals(kind) && ver != null && ver >= 1;
    }
}
