package nl.hauntedmc.serverfeatures.features.bettercoral;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.bettercoral.listener.BetterCoralListener;
import nl.hauntedmc.serverfeatures.features.bettercoral.meta.Meta;
import nl.hauntedmc.serverfeatures.features.bettercoral.recipe.CoralRecipes;

import java.util.HashMap;
import java.util.Map;

public final class BetterCoral extends BukkitBaseFeature<Meta> {

    private CoralRecipes recipes;

    public BetterCoral(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap config = new ConfigMap();
        config.put("enabled", false);

        Map<String, Object> furnace = new HashMap<>();
        furnace.put("enabled", true);
        furnace.put("cook_time_ticks", 200);
        furnace.put("experience", 0.0D);
        config.put("furnace", furnace);
        return config;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        boolean furnaceEnabled = getConfigHandler()
                .node("furnace")
                .get("enabled")
                .as(Boolean.class, true);
        if (furnaceEnabled) {
            CoralRecipes configuredRecipes = new CoralRecipes(this);
            configuredRecipes.registerAll();
            recipes = configuredRecipes;
        }

        getLifecycleManager().getListenerManager().registerListener(new BetterCoralListener());
    }

    @Override
    public void disable() {
        if (recipes != null) {
            recipes.unregisterAll();
            recipes = null;
        }
    }
}
