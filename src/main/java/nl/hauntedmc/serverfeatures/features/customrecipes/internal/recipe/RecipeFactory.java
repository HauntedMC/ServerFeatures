package nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe;

import nl.hauntedmc.serverfeatures.features.customrecipes.CustomRecipes;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeData;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeType;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe.impl.*;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;

public class RecipeFactory {

    private static final Map<RecipeType, CustomRecipe> CUSTOM_RECIPE_MAP = new HashMap<>();

    static {
        CUSTOM_RECIPE_MAP.put(RecipeType.SHAPED, new CustomShapedRecipe());
        CUSTOM_RECIPE_MAP.put(RecipeType.SHAPELESS, new CustomShapelessRecipe());
        CUSTOM_RECIPE_MAP.put(RecipeType.FURNACE, new CustomFurnaceRecipe());
        CUSTOM_RECIPE_MAP.put(RecipeType.BLASTING, new CustomBlastingRecipe());
        CUSTOM_RECIPE_MAP.put(RecipeType.SMOKING, new CustomSmokingRecipe());
        CUSTOM_RECIPE_MAP.put(RecipeType.CAMPFIRE, new CustomCampfireRecipe());
        CUSTOM_RECIPE_MAP.put(RecipeType.STONECUTTING, new CustomStonecutterRecipe());
        CUSTOM_RECIPE_MAP.put(RecipeType.SMITHING, new CustomSmithingRecipe());
        CUSTOM_RECIPE_MAP.put(RecipeType.DISABLE, new CustomDisableRecipe());
    }

    public static RecipeData createRecipe(CustomRecipes feature, Map<?, ?> config, int index) {
        // Determine a unique key for this recipe.
        String keyStr = config.containsKey("key")
                ? config.get("key").toString().toLowerCase()
                : "custom_recipe_" + index;
        NamespacedKey key;
        if (keyStr.contains(":")) {
            String[] parts = keyStr.split(":", 2);
            key = new NamespacedKey(parts[0], parts[1]);
        } else {
            key = new NamespacedKey(feature.getPlugin(), keyStr);
        }

        // Determine the recipe type (default to "shaped" if not provided).
        String typeStr = config.containsKey("type")
                ? config.get("type").toString().toLowerCase()
                : "shaped";
        RecipeType type;
        try {
            type = RecipeType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            feature.getLogger().warning("Invalid recipe type for key " + keyStr + ": " + typeStr);
            return null;
        }

        CustomRecipe customRecipe = CUSTOM_RECIPE_MAP.get(type);
        if (customRecipe == null) {
            feature.getLogger().warning("No implementation for recipe type: " + typeStr);
            return null;
        }
        return customRecipe.createRecipe(feature, key, config);
    }
}
