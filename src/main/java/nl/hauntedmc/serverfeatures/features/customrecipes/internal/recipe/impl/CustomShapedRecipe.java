package nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe.impl;

import nl.hauntedmc.serverfeatures.features.customrecipes.CustomRecipes;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeData;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.util.List;
import java.util.Map;

public class CustomShapedRecipe extends AbstractCustomRecipe {

    @SuppressWarnings("unchecked")
    @Override
    public RecipeData createRecipe(CustomRecipes feature, NamespacedKey key, Map<?, ?> config) {
        ItemStack output = getOutput(feature, config, key);
        if (output == null) {
            return null;
        }
        Object shapeObj = config.get("shape");
        List<String> shapeList;
        if (shapeObj instanceof List) {
            shapeList = (List<String>) shapeObj;
        } else {
            shapeList = List.of(shapeObj.toString().split(","));
        }
        ShapedRecipe shaped = new ShapedRecipe(key, output);
        shaped.shape(shapeList.toArray(new String[0]));

        Object ingredientsObj = config.get("ingredients");
        if (!(ingredientsObj instanceof Map<?, ?> ingredientsMap)) {
            feature.getLogger().warning("Shaped recipe " + key + " missing ingredients mapping.");
            return null;
        }
        for (Map.Entry<?, ?> entry : ingredientsMap.entrySet()) {
            String symbol = entry.getKey().toString().trim();
            if (symbol.length() != 1) {
                feature.getLogger().warning("Invalid ingredient key in shaped recipe " + key + ": " + symbol);
                continue;
            }
            char ch = symbol.charAt(0);
            String materialStr = entry.getValue().toString().trim();
            try {
                Material material = Material.valueOf(materialStr.toUpperCase());
                shaped.setIngredient(ch, new RecipeChoice.MaterialChoice(material));
            } catch (IllegalArgumentException e) {
                feature.getLogger().warning("Unknown material for ingredient '" + ch + "' in recipe " + key + ": " + materialStr);
            }
        }
        return new RecipeData(key, shaped, RecipeType.SHAPED);
    }
}
