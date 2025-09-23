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
import org.bukkit.inventory.SmithingTransformRecipe;

import java.util.Map;

public class CustomSmithingRecipe implements CustomRecipe {

    @Override
    public RecipeData createRecipe(CustomRecipes feature, NamespacedKey key, Map<?, ?> config) {
        if (!config.containsKey("base") || !config.containsKey("addition") || !config.containsKey("result")) {
            feature.getLogger().warning("Smithing recipe " + key + " missing base, addition, or result.");
            return null;
        }

        // Parse base
        final String baseStr = String.valueOf(config.get("base")).trim();
        final Material baseMaterial;
        try {
            baseMaterial = Material.valueOf(baseStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            feature.getLogger().warning("Unknown base material in smithing recipe " + key + ": " + baseStr);
            return null;
        }

        // Parse addition
        final String additionStr = String.valueOf(config.get("addition")).trim();
        final Material additionMaterial;
        try {
            additionMaterial = Material.valueOf(additionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            feature.getLogger().warning("Unknown addition material in smithing recipe " + key + ": " + additionStr);
            return null;
        }

        // Parse result
        final ItemStack result = ParseUtils.parseItemStack(String.valueOf(config.get("result")));
        if (result == null) {
            feature.getLogger().warning("Failed to parse result for smithing recipe " + key);
            return null;
        }

        // Smithing in modern API requires a TEMPLATE item.
        // Prefer a configured template; fall back to NETHERITE_UPGRADE_SMITHING_TEMPLATE if missing.
        Material templateMaterial = Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE;
        if (config.containsKey("template")) {
            final String templateStr = String.valueOf(config.get("template")).trim();
            try {
                templateMaterial = Material.valueOf(templateStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                feature.getLogger().warning("Unknown template material in smithing recipe " + key + ": " + templateStr +
                        " (defaulting to NETHERITE_UPGRADE_SMITHING_TEMPLATE)");
            }
        } else {
            feature.getLogger().info("Smithing recipe " + key + " has no 'template' set; defaulting to NETHERITE_UPGRADE_SMITHING_TEMPLATE.");
        }

        SmithingTransformRecipe recipe = new SmithingTransformRecipe(
                key,
                result,
                new RecipeChoice.MaterialChoice(templateMaterial),
                new RecipeChoice.MaterialChoice(baseMaterial),
                new RecipeChoice.MaterialChoice(additionMaterial)
        );

        return new RecipeData(key, recipe, RecipeType.SMITHING);
    }
}
