package nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe.impl;

import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeData;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

public class CustomShapelessRecipe extends AbstractCustomRecipe {

    @Override
    public RecipeData createRecipe(JavaPlugin plugin, NamespacedKey key, Map<?, ?> config) {
        ItemStack output = getOutput(plugin, config, key);
        if (output == null) {
            return null;
        }
        Object ingredientsObj = config.get("ingredients");
        if (!(ingredientsObj instanceof List)) {
            plugin.getLogger().warning("Shapeless recipe " + key.toString() + " missing ingredients list.");
            return null;
        }
        List<?> ingredientsList = (List<?>) ingredientsObj;
        ShapelessRecipe shapeless = new ShapelessRecipe(key, output);
        for (Object ing : ingredientsList) {
            String materialStr = ing.toString().trim();
            try {
                Material material = Material.valueOf(materialStr.toUpperCase());
                shapeless.addIngredient(material);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown ingredient in shapeless recipe " + key.toString() + ": " + materialStr);
            }
        }
        return new RecipeData(key, shapeless, RecipeType.SHAPELESS);
    }
}
