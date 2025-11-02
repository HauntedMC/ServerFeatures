package nl.hauntedmc.serverfeatures.features.customrecipes.config;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigService;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigView;
import nl.hauntedmc.serverfeatures.features.customrecipes.CustomRecipes;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.RecipeData;
import nl.hauntedmc.serverfeatures.features.customrecipes.internal.recipe.RecipeFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Recipes config backed by the unified config system.
 * File: local/recipes.yml
 */
public final class RecipeConfigHandler extends ConfigView {

    private final CustomRecipes feature;

    public RecipeConfigHandler(CustomRecipes feature) {
        super(new ConfigService(feature.getPlugin()).open("local/recipes.yml", /* copyDefaultsIfPresent */ true), "");
        this.feature = feature;
    }

    /**
     * Loads recipes from the 'recipes' list in local/recipes.yml.
     */
    public List<RecipeData> loadRecipes() {
        List<RecipeData> out = new ArrayList<>();

        // Read as List<Map<?, ?>> to avoid generic mismatch; RecipeFactory accepts Map<?, ?>.
        List<Map> defs = getList("recipes", Map.class, List.of());
        if (defs.isEmpty()) {
            feature.getLogger().severe("recipes.yml does not contain a non-empty 'recipes' list!");
            return out;
        }

        int index = 0;
        for (Map<?, ?> def : defs) {
            RecipeData rd = RecipeFactory.createRecipe(feature, def, index++);
            if (rd != null) out.add(rd);
        }
        return out;
    }

    /** Reloads the YAML from disk. */
    public void reload() {
        file.reload();
    }

    /**
     * No-op: all write operations via put/batch/compute auto-save.
     * Kept for backwards compatibility with the previous API.
     */
    public void save() {
        // intentionally empty
    }
}
