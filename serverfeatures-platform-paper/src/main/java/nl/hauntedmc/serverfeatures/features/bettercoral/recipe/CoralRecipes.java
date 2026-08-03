package nl.hauntedmc.serverfeatures.features.bettercoral.recipe;

import nl.hauntedmc.serverfeatures.features.bettercoral.BetterCoral;
import nl.hauntedmc.serverfeatures.features.bettercoral.CoralConversion;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CoralRecipes {

    private static final int MIN_COOK_TIME_TICKS = 1;
    private static final int MAX_COOK_TIME_TICKS = 72_000;

    private final BetterCoral feature;
    private final int cookTime;
    private final float experience;
    private final List<NamespacedKey> registeredKeys = new ArrayList<>();

    public CoralRecipes(BetterCoral feature) {
        this.feature = feature;
        int configuredCookTime = feature.getConfigHandler().node("furnace").get("cook_time_ticks")
                .as(Integer.class, 200);
        double configuredExperience = feature.getConfigHandler().node("furnace").get("experience")
                .as(Double.class, 0.0D);

        this.cookTime = Math.max(MIN_COOK_TIME_TICKS, Math.min(MAX_COOK_TIME_TICKS, configuredCookTime));
        this.experience = (float) Math.max(0.0D, configuredExperience);
    }

    public void registerAll() {
        var server = feature.getPlugin().getServer();

        for (Map.Entry<Material, Material> conversion : CoralConversion.itemConversions().entrySet()) {
            Material input = conversion.getKey();
            Material output = conversion.getValue();
            NamespacedKey key = recipeKey(input);

            // Remove a stale copy left by a plugin reload before registering the current definition.
            server.removeRecipe(key);

            FurnaceRecipe recipe = new FurnaceRecipe(
                    key,
                    new ItemStack(output),
                    new RecipeChoice.MaterialChoice(input),
                    experience,
                    cookTime
            );

            if (!server.addRecipe(recipe)) {
                feature.getPlugin().getLogger().warning("Could not register BetterCoral recipe " + key);
                continue;
            }
            registeredKeys.add(key);
        }
    }

    public void unregisterAll() {
        var server = feature.getPlugin().getServer();
        for (NamespacedKey key : registeredKeys) {
            server.removeRecipe(key);
        }
        registeredKeys.clear();
    }

    private NamespacedKey recipeKey(Material input) {
        return new NamespacedKey(feature.getPlugin(),
                "coral_dry_" + input.name().toLowerCase(Locale.ROOT));
    }
}
