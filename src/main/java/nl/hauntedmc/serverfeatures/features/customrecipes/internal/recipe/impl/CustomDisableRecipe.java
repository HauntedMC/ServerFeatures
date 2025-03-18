package nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe.impl;

import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeData;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeType;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe.CustomRecipe;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class CustomDisableRecipe implements CustomRecipe {

    @Override
    public RecipeData createRecipe(JavaPlugin plugin, NamespacedKey key, Map<?, ?> config) {
        return new RecipeData(key, Bukkit.getRecipe(key), RecipeType.DISABLE);
    }
}
