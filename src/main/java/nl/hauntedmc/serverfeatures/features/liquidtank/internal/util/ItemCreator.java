package nl.hauntedmc.serverfeatures.features.liquidtank.internal.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ItemCreator {
    public static ItemStack newItem(Material paramMaterial, int paramInt, String paramString1, String paramString2) {
        ItemStack itemStack = new ItemStack(paramMaterial);
        itemStack.setAmount(paramInt);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            if (!paramString2.isEmpty()) {
                // Split on "||" (must escape for regex)
                List<Component> lore = Arrays.stream(paramString2.split("\\|\\|"))
                        .map(Component::text)
                        .collect(Collectors.toList());
                itemMeta.lore(lore);
            }
            if (!paramString1.isEmpty()) {
                itemMeta.displayName(Component.text(paramString1));
            }
            itemStack.setItemMeta(itemMeta);
        }
        return itemStack;
    }
}
