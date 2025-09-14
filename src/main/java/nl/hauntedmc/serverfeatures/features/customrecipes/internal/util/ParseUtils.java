package nl.hauntedmc.serverfeatures.features.customrecipes.internal.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ParseUtils {

    /**
     * Parses a string of the format "MATERIAL,amount" into an ItemStack.
     * Returns null if the material is unknown.
     */
    public static ItemStack parseItemStack(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        String[] parts = input.split(",");
        if (parts.length == 0) {
            return null;
        }
        String materialName = parts[0].trim();
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
        int amount = 1;
        if (parts.length > 1) {
            try {
                amount = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return new ItemStack(material, amount);
    }

    public static float parseFloat(Object obj, float def) {
        if (obj == null) return def;
        try {
            return Float.parseFloat(obj.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static int parseInt(Object obj, int def) {
        if (obj == null) return def;
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
