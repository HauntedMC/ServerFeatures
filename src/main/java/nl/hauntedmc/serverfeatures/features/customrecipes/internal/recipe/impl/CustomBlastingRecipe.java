package nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe.impl;

import nl.hauntedmc.serverfeatures.features.customrecipes.CustomRecipes;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeData;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class CustomBlastingRecipe extends AbstractCustomRecipe {

    @Override
    public RecipeData createRecipe(CustomRecipes feature, NamespacedKey key, Map<?, ?> config) {
        ItemStack output = getOutput(feature, config, key);
        if (output == null) {
            return null;
        }
        if (!config.containsKey("input")) {
            feature.getLogger().warning("Blasting recipe " + key.toString() + " missing input.");
            return null;
        }
        String inputStr = config.get("input").toString().trim();
        Material inputMaterial;
        try {
            inputMaterial = Material.valueOf(inputStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            feature.getLogger().warning("Unknown input material in blasting recipe " + key.toString() + ": " + inputStr);
            return null;
        }
        float experience = config.containsKey("experience")
                ? Float.parseFloat(config.get("experience").toString()) : 0.0f;
        int cookingTime = config.containsKey("cooking-time")
                ? Integer.parseInt(config.get("cooking-time").toString()) : 100;
        BlastingRecipe recipe = new BlastingRecipe(key, output, inputMaterial, experience, cookingTime);
        return new RecipeData(key, recipe, RecipeType.BLASTING);
    }
}
