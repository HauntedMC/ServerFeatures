package nl.hauntedmc.serverfeatures.features.customrecipes.internal;

import nl.hauntedmc.serverfeatures.features.customrecipes.CustomRecipes;
import nl.hauntedmc.serverfeatures.features.customrecipes.config.RecipeConfigHandler;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Service class that handles loading, registration, and enabling/disabling of recipes.
 */
public class RecipeService {

    private final RecipeRepository repository;
    private final CustomRecipes feature;

    public RecipeService(CustomRecipes feature) {
        this.feature = feature;
        this.repository = new RecipeRepository();
    }

    /**
     * Loads recipes from the configuration file and registers them.
     */
    public void loadRecipes() {
        RecipeConfigHandler loader = new RecipeConfigHandler(feature);
        List<RecipeData> recipes = loader.loadRecipes();
        for (RecipeData data : recipes) {
            NamespacedKey key = data.getKey();
            if (data.getType() == RecipeType.DISABLE) {
                Recipe vanillaRecipe = Bukkit.getRecipe(key);
                if (vanillaRecipe != null) {
                    if (Bukkit.removeRecipe(key)) {
                        feature.getPlugin().getLogger().info("Disabled vanilla recipe with key: " + key);
                        data.setRecipe(vanillaRecipe);
                    } else {
                        feature.getPlugin().getLogger().warning("Could not disable vanilla recipe with key: " + key);
                    }
                } else {
                    feature.getPlugin().getLogger().warning("Vanilla recipe not found for key: " + key);
                }
            } else {
                if (Bukkit.getRecipe(key) != null) {
                    if (Bukkit.removeRecipe(key)) {
                        feature.getPlugin().getLogger().info("Removed existing recipe with key: " + key);
                    } else {
                        feature.getPlugin().getLogger().warning("Could not remove existing recipe with key: " + key);
                    }
                }
                Bukkit.addRecipe(data.getRecipe());
                feature.getPlugin().getLogger().info("Registered recipe with key: " + key);
            }
            repository.registerRecipe(data);
        }
    }

    /**
     * Unregisters all recipes.
     */
    public void unregisterRecipes() {
        for (RecipeData data : repository.getAllRecipes()) {
            NamespacedKey key = data.getKey();
            if (!repository.isDisabled(key)) {
                if (data.getType() == RecipeType.DISABLE) {
                    if (data.getRecipe() != null) {
                        Bukkit.addRecipe(data.getRecipe());
                        feature.getPlugin().getLogger().info("Restored vanilla recipe with key: " + key.toString());
                    }
                } else {
                    if (Bukkit.getRecipe(key) != null) {
                        if (Bukkit.removeRecipe(key)) {
                            feature.getPlugin().getLogger().info("Removed custom recipe with key: " + key);
                        } else {
                            feature.getPlugin().getLogger().warning("Failed to remove custom recipe with key: " + key);
                        }
                    }
                }
            }
        }
        repository.clear();
    }

    /**
     * Temporarily disables a recipe.
     *
     * @param key The NamespacedKey of the recipe.
     * @return true if successful, false otherwise.
     */
    public boolean disableRecipe(NamespacedKey key) {
        RecipeData data = repository.getRecipe(key);
        if (data == null || repository.isDisabled(key)) {
            return false;
        }
        if (data.getType() == RecipeType.DISABLE) {
            if (data.getRecipe() != null) {
                if (Bukkit.addRecipe(data.getRecipe())) {
                    repository.markDisabled(key);
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            if (Bukkit.getRecipe(key) != null) {
                if (Bukkit.removeRecipe(key)) {
                    repository.markDisabled(key);
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    /**
     * Re-enables a previously disabled recipe.
     *
     * @param key The NamespacedKey of the recipe.
     * @return true if successful, false otherwise.
     */
    public boolean enableRecipe(NamespacedKey key) {
        RecipeData data = repository.getRecipe(key);
        if (data == null || !repository.isDisabled(key)) {
            return false;
        }
        if (data.getType() == RecipeType.DISABLE) {
            if (Bukkit.getRecipe(key) != null) {
                Bukkit.removeRecipe(key);
                repository.markEnabled(key);
                return true;
            } else {
                return false;
            }
        } else {
            Bukkit.addRecipe(data.getRecipe());
            repository.markEnabled(key);
            return true;
        }
    }

    /**
     * Returns a list of active recipe keys as strings.
     */
    public List<String> getActiveRecipeKeys() {
        List<String> keys = new ArrayList<>();
        for (RecipeData data : repository.getActiveRecipes()) {
            keys.add(data.getKey().toString());
        }
        return keys;
    }

    /**
     * Returns a list of disabled recipe keys as strings.
     */
    public List<String> getDisabledRecipeKeys() {
        List<String> keys = new ArrayList<>();
        for (RecipeData data : repository.getDisabledRecipes()) {
            keys.add(data.getKey().toString());
        }
        return keys;
    }

    /**
     * Returns the RecipeData for a given key.
     */
    public RecipeData getRecipeData(NamespacedKey key) {
        return repository.getRecipe(key);
    }
}
