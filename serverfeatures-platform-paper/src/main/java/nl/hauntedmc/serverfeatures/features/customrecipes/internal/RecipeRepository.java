package nl.hauntedmc.serverfeatures.features.customrecipes.internal;

import org.bukkit.NamespacedKey;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Repository for managing recipe data.
 */
public class RecipeRepository {

    private final Map<NamespacedKey, RecipeData> registeredRecipes = new HashMap<>();
    private final Set<NamespacedKey> disabledKeys = new HashSet<>();

    public void registerRecipe(RecipeData data) {
        registeredRecipes.put(data.getKey(), data);
    }

    public RecipeData getRecipe(NamespacedKey key) {
        return registeredRecipes.get(key);
    }

    public boolean isDisabled(NamespacedKey key) {
        return disabledKeys.contains(key);
    }

    public void markDisabled(NamespacedKey key) {
        disabledKeys.add(key);
    }

    public void markEnabled(NamespacedKey key) {
        disabledKeys.remove(key);
    }

    public Collection<RecipeData> getAllRecipes() {
        return registeredRecipes.values();
    }

    public Collection<RecipeData> getActiveRecipes() {
        return registeredRecipes.values().stream()
                .filter(data -> !disabledKeys.contains(data.getKey()))
                .collect(Collectors.toList());
    }

    public Collection<RecipeData> getDisabledRecipes() {
        return registeredRecipes.values().stream()
                .filter(data -> disabledKeys.contains(data.getKey()))
                .collect(Collectors.toList());
    }

    public void clear() {
        registeredRecipes.clear();
        disabledKeys.clear();
    }
}
