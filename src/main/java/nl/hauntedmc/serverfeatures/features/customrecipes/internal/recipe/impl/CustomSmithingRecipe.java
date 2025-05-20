package nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe.impl;

import nl.hauntedmc.serverfeatures.features.customrecipes.CustomRecipes;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeData;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeType;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe.CustomRecipe;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.util.ParseUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class CustomSmithingRecipe implements CustomRecipe {

    @Override
    public RecipeData createRecipe(CustomRecipes feature, NamespacedKey key, Map<?, ?> config) {
        if (!config.containsKey("base") || !config.containsKey("addition") || !config.containsKey("result")) {
            feature.getLogger().warning("Smithing recipe " + key.toString() + " missing base, addition, or result.");
            return null;
        }
        String baseStr = config.get("base").toString().trim();
        Material baseMaterial;
        try {
            baseMaterial = Material.valueOf(baseStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            feature.getLogger().warning("Unknown base material in smithing recipe " + key.toString() + ": " + baseStr);
            return null;
        }
        String additionStr = config.get("addition").toString().trim();
        Material additionMaterial;
        try {
            additionMaterial = Material.valueOf(additionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            feature.getLogger().warning("Unknown addition material in smithing recipe " + key.toString() + ": " + additionStr);
            return null;
        }
        ItemStack result = ParseUtils.parseItemStack(config.get("result").toString());
        if (result == null) {
            feature.getLogger().warning("Failed to parse result for smithing recipe " + key.toString());
            return null;
        }
        SmithingRecipe recipe = new SmithingRecipe(key, result,
                new RecipeChoice.MaterialChoice(baseMaterial),
                new RecipeChoice.MaterialChoice(additionMaterial));
        return new RecipeData(key, recipe, RecipeType.SMITHING);
    }
}
