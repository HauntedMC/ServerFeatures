package nl.hauntedmc.serverfeatures.features.customrecipes.internal;

import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecipeDataTest {

    @Test
    void storesKeyRecipeAndType() {
        NamespacedKey key = NamespacedKey.minecraft("test_recipe");
        Recipe first = InterfaceProxy.of(Recipe.class, Map.of());
        Recipe second = InterfaceProxy.of(Recipe.class, Map.of());

        RecipeData data = new RecipeData(key, first, RecipeType.SHAPED);
        assertEquals(key, data.getKey());
        assertEquals(first, data.getRecipe());
        assertEquals(RecipeType.SHAPED, data.getType());

        data.setRecipe(second);
        assertEquals(second, data.getRecipe());
    }
}

