package nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe.impl;

import nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe.CustomRecipe;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.util.ParseUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Base class for custom recipe implementations providing common utility methods.
 */
public abstract class AbstractCustomRecipe implements CustomRecipe {

    protected ItemStack getOutput(JavaPlugin plugin, Map<?, ?> config, NamespacedKey key) {
        if (!config.containsKey("output")) {
            plugin.getLogger().warning("Missing output for recipe key: " + key.toString());
            return null;
        }
        ItemStack output = ParseUtils.parseItemStack(config.get("output").toString(), plugin);
        if (output == null) {
            plugin.getLogger().warning("Failed to parse output for recipe key: " + key.toString());
        }
        return output;
    }
}
