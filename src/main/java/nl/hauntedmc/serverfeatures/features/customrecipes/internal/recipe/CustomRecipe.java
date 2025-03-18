package nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe;

import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeData;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Map;

public interface CustomRecipe {
    /**
     * Creates a recipe (wrapped in RecipeData) from the given configuration.
     *
     * @param plugin The plugin instance.
     * @param key    The NamespacedKey for the recipe.
     * @param config The configuration map for this recipe.
     * @return A RecipeData object if creation succeeded; otherwise null.
     */
    RecipeData createRecipe(JavaPlugin plugin, NamespacedKey key, Map<?, ?> config);
}
