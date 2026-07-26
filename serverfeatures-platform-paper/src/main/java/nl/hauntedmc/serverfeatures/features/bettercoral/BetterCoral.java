package nl.hauntedmc.serverfeatures.features.bettercoral;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.bettercoral.listener.BetterCoralListener;
import nl.hauntedmc.serverfeatures.features.bettercoral.meta.Meta;
import nl.hauntedmc.serverfeatures.features.bettercoral.recipe.CoralRecipes;

import java.util.HashMap;
import java.util.Map;

public final class BetterCoral extends BukkitBaseFeature<Meta> {

    private CoralRecipes recipes;

    public BetterCoral(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap c = new ConfigMap();
        c.put("enabled", false);
        Map<String, Object> furnace = new HashMap<>();
        furnace.put("enabled", true);
        furnace.put("cook_time_ticks", 2);
        furnace.put("experience", 0.0D);
        c.put("furnace", furnace);
        return c;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        getLifecycleManager().getListenerManager().registerListener(new BetterCoralListener());
        boolean furnaceEnabled = getConfigHandler().node("furnace").get("enabled").as(Boolean.class, true);
        if (furnaceEnabled) {
            recipes = new CoralRecipes(this);
            recipes.registerAll();
        }
    }

    @Override
    public void disable() {
        if (recipes != null) recipes.unregisterAll();
    }
}
