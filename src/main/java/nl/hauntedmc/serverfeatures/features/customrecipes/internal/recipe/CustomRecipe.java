package nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe;

import nl.hauntedmc.serverfeatures.features.customrecipes.CustomRecipes;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeData;
import org.bukkit.NamespacedKey;
import java.util.Map;

public interface CustomRecipe {
    /**
     * Creates a recipe (wrapped in RecipeData) from the given configuration.
     *
     * @param feature The feature instance.
     * @param key    The NamespacedKey for the recipe.
     * @param config The configuration map for this recipe.
     * @return A RecipeData object if creation succeeded; otherwise null.
     */
    RecipeData createRecipe(CustomRecipes feature, NamespacedKey key, Map<?, ?> config);
}
