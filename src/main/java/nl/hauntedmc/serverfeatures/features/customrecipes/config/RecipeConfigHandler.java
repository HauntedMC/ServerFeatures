package nl.hauntedmc.serverfeatures.features.customrecipes.config;

import nl.hauntedmc.serverfeatures.features.customrecipes.CustomRecipes;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeData;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe.RecipeFactory;
import nl.hauntedmc.serverfeatures.common.resources.ResourceHandler;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RecipeConfigHandler {

    private final ResourceHandler resourceHandler;
    private final CustomRecipes feature;
    private FileConfiguration config;

    public RecipeConfigHandler(CustomRecipes feature) {
        this.feature = feature;
        // Initialize the ResourceHandler for recipes.yml.
        this.resourceHandler = new ResourceHandler(feature.getPlugin(), "recipes.yml");
        this.config = resourceHandler.getConfig();
    }

    /**
     * Loads recipes from the recipes.yml file.
     *
     * @return A list of RecipeData objects loaded from the file.
     */
    public List<RecipeData> loadRecipes() {
        List<RecipeData> recipes = new ArrayList<>();
        if (!config.contains("recipes")) {
            feature.getLogger().severe("recipes.yml does not contain a 'recipes' section!");
            return recipes;
        }
        List<Map<?, ?>> recipesList = config.getMapList("recipes");
        int index = 0;
        for (Map<?, ?> recipeConfig : recipesList) {
            RecipeData recipeData = RecipeFactory.createRecipe(feature, recipeConfig, index);
            if (recipeData != null) {
                recipes.add(recipeData);
            }
            index++;
        }
        return recipes;
    }

    /**
     * Reloads the recipes configuration from disk.
     */
    public void reload() {
        resourceHandler.reload();
        this.config = resourceHandler.getConfig();
    }

    /**
     * Saves the current recipes configuration to disk.
     */
    public void save() {
        resourceHandler.save();
    }
}
