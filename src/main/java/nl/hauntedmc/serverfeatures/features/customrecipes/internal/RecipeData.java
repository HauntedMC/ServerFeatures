package nl.hauntedmc.serverfeatures.features.customrecipes.internal;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;

public class RecipeData {
    private final NamespacedKey key;
    private Recipe recipe;
    private final RecipeType type;

    public RecipeData(NamespacedKey key, Recipe recipe, RecipeType type) {
        this.key = key;
        this.recipe = recipe;
        this.type = type;
    }

    public NamespacedKey getKey() {
        return key;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public RecipeType getType() {
        return type;
    }

    public void setRecipe(Recipe recipe) {
        this.recipe = recipe;
    }
}
