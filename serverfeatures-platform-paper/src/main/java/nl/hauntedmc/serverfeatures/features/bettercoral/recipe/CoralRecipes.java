package nl.hauntedmc.serverfeatures.features.bettercoral.recipe;

import nl.hauntedmc.serverfeatures.features.bettercoral.BetterCoral;
import nl.hauntedmc.serverfeatures.features.bettercoral.internal.CoralMaterials;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CoralRecipes {

    private static final int DEFAULT_COOK_TIME_TICKS = 200;
    private static final int MAX_COOK_TIME_TICKS = 72_000;

    private final BetterCoral feature;
    private final int cookTime;
    private final float experience;
    private final Set<NamespacedKey> registeredKeys = new LinkedHashSet<>();

    public CoralRecipes(BetterCoral feature) {
        this.feature = feature;

        int configuredCookTime = feature.getConfigHandler()
                .node("furnace")
                .get("cook_time_ticks")
                .as(Integer.class, DEFAULT_COOK_TIME_TICKS);
        this.cookTime = Math.max(1, Math.min(MAX_COOK_TIME_TICKS, configuredCookTime));
        if (configuredCookTime != cookTime) {
            feature.getLogger().warning(
                    "BetterCoral furnace.cook_time_ticks was outside the supported range and was clamped to "
                            + cookTime
            );
        }

        double configuredExperience = feature.getConfigHandler()
                .node("furnace")
                .get("experience")
                .as(Double.class, 0.0D);
        this.experience = sanitizeExperience(configuredExperience);
        if (!Double.isFinite(configuredExperience) || configuredExperience < 0.0D) {
            feature.getLogger().warning(
                    "BetterCoral furnace.experience must be finite and non-negative; using " + experience
            );
        }
    }

    public void registerAll() {
        unregisterAll();

        Server server = feature.getPlugin().getServer();
        List<NamespacedKey> addedThisRun = new ArrayList<>();
        try {
            for (Map.Entry<Material, Material> conversion
                    : CoralMaterials.furnaceConversions().entrySet()) {
                Material input = conversion.getKey();
                Material result = conversion.getValue();
                NamespacedKey key = keyFor(input);

                removeExistingRecipe(server, key);

                FurnaceRecipe recipe = new FurnaceRecipe(
                        key,
                        new ItemStack(result),
                        new RecipeChoice.MaterialChoice(input),
                        experience,
                        cookTime
                );
                if (!server.addRecipe(recipe)) {
                    throw new IllegalStateException("Could not register BetterCoral recipe " + key);
                }
                addedThisRun.add(key);
            }
        } catch (RuntimeException exception) {
            rollback(server, addedThisRun);
            throw exception;
        }

        registeredKeys.addAll(addedThisRun);
        feature.getLogger().info("Registered " + registeredKeys.size() + " BetterCoral furnace recipes");
    }

    public void unregisterAll() {
        if (registeredKeys.isEmpty()) {
            return;
        }

        Server server = feature.getPlugin().getServer();
        for (NamespacedKey key : registeredKeys) {
            if (server.getRecipe(key) != null && !server.removeRecipe(key)) {
                feature.getLogger().warning("Could not unregister BetterCoral recipe " + key);
            }
        }
        registeredKeys.clear();
    }

    private NamespacedKey keyFor(Material input) {
        return new NamespacedKey(
                feature.getPlugin(),
                "coral_burn_" + input.name().toLowerCase(Locale.ROOT)
        );
    }

    private static void removeExistingRecipe(Server server, NamespacedKey key) {
        if (server.getRecipe(key) != null && !server.removeRecipe(key)) {
            throw new IllegalStateException("Could not replace existing BetterCoral recipe " + key);
        }
    }

    private static void rollback(Server server, List<NamespacedKey> addedKeys) {
        for (NamespacedKey key : addedKeys) {
            server.removeRecipe(key);
        }
    }

    private static float sanitizeExperience(double configuredExperience) {
        if (!Double.isFinite(configuredExperience) || configuredExperience <= 0.0D) {
            return 0.0F;
        }
        return (float) Math.min(Float.MAX_VALUE, configuredExperience);
    }
}
