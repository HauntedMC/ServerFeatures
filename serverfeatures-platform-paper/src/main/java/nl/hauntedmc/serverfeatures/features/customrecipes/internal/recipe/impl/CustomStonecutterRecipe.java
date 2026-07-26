package nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe.impl;

import nl.hauntedmc.serverfeatures.features.customrecipes.CustomRecipes;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeData;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.Map;

public class CustomStonecutterRecipe extends AbstractCustomRecipe {

    @Override
    public RecipeData createRecipe(CustomRecipes feature, NamespacedKey key, Map<?, ?> config) {
        ItemStack output = getOutput(feature, config, key);
        if (output == null) {
            return null;
        }
        if (!config.containsKey("input")) {
            feature.getLogger().warning("Stonecutter recipe " + key.toString() + " missing input.");
            return null;
        }
        String inputStr = config.get("input").toString().trim();
        Material inputMaterial;
        try {
            inputMaterial = Material.valueOf(inputStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            feature.getLogger().warning("Unknown input material in stonecutter recipe " + key.toString() + ": " + inputStr);
            return null;
        }
        StonecuttingRecipe recipe = new StonecuttingRecipe(key, output, inputMaterial);
        return new RecipeData(key, recipe, RecipeType.STONECUTTING);
    }
}
