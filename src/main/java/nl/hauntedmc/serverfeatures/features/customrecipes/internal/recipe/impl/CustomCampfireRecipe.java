package nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe.impl;

import nl.hauntedmc.serverfeatures.features.customrecipes.CustomRecipes;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeData;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class CustomCampfireRecipe extends AbstractCustomRecipe {

    @Override
    public RecipeData createRecipe(CustomRecipes feature, NamespacedKey key, Map<?, ?> config) {
        ItemStack output = getOutput(feature, config, key);
        if (output == null) {
            return null;
        }
        if (!config.containsKey("input")) {
            feature.getLogger().warning("Campfire recipe " + key.toString() + " missing input.");
            return null;
        }
        String inputStr = config.get("input").toString().trim();
        Material inputMaterial;
        try {
            inputMaterial = Material.valueOf(inputStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            feature.getLogger().warning("Unknown input material in campfire recipe " + key.toString() + ": " + inputStr);
            return null;
        }
        float experience = config.containsKey("experience")
                ? Float.parseFloat(config.get("experience").toString()) : 0.0f;
        int cookingTime = config.containsKey("cooking-time")
                ? Integer.parseInt(config.get("cooking-time").toString()) : 100;
        CampfireRecipe recipe = new CampfireRecipe(key, output, inputMaterial, experience, cookingTime);
        return new RecipeData(key, recipe, RecipeType.CAMPFIRE);
    }
}
