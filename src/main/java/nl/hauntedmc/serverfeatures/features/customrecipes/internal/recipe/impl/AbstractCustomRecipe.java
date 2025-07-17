package nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe.impl;

import nl.hauntedmc.serverfeatures.features.customrecipes.CustomRecipes;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe.CustomRecipe;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.util.ParseUtils;
import nl.hauntedmc.serverfeatures.internal.FeatureLogger;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Base class for custom recipe implementations providing common utility methods.
 */
public abstract class AbstractCustomRecipe implements CustomRecipe {

    protected ItemStack getOutput(CustomRecipes feature, Map<?, ?> config, NamespacedKey key) {
        if (!config.containsKey("output")) {
            feature.getLogger().warning("Missing output for recipe key: " + key.toString());
            return null;
        }
        ItemStack output = ParseUtils.parseItemStack(config.get("output").toString());
        if (output == null) {
            feature.getLogger().warning("Failed to parse output for recipe key: " + key.toString());
        }
        return output;
    }
}
