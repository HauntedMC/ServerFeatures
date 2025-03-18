package nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe.impl;

import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeData;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class CustomSmokingRecipe extends AbstractCustomRecipe {

    @Override
    public RecipeData createRecipe(JavaPlugin plugin, NamespacedKey key, Map<?, ?> config) {
        ItemStack output = getOutput(plugin, config, key);
        if (output == null) {
            return null;
        }
        if (!config.containsKey("input")) {
            plugin.getLogger().warning("Smoking recipe " + key.toString() + " missing input.");
            return null;
        }
        String inputStr = config.get("input").toString().trim();
        Material inputMaterial;
        try {
            inputMaterial = Material.valueOf(inputStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown input material in smoking recipe " + key.toString() + ": " + inputStr);
            return null;
        }
        float experience = config.containsKey("experience")
                ? Float.parseFloat(config.get("experience").toString()) : 0.0f;
        int cookingTime = config.containsKey("cooking-time")
                ? Integer.parseInt(config.get("cooking-time").toString()) : 100;
        SmokingRecipe recipe = new SmokingRecipe(key, output, inputMaterial, experience, cookingTime);
        return new RecipeData(key, recipe, RecipeType.SMOKING);
    }
}
