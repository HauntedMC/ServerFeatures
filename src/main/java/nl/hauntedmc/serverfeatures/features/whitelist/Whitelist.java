package nl.hauntedmc.serverfeatures.features.whitelist;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BaseFeature;
import nl.hauntedmc.serverfeatures.features.whitelist.listener.PlayerLoginListener;
import nl.hauntedmc.serverfeatures.features.whitelist.meta.Meta;
import nl.hauntedmc.serverfeatures.localization.MessageMap;

import java.util.HashMap;
import java.util.Map;

public class Whitelist extends BaseFeature<Meta> {

    public Whitelist(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    /**
     * Returns default config values for the Titles feature.
     */
    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        return defaults;
    }

    /**
     * Returns the default messages for the Titles feature.
     */
    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    /**
     * Initialize the feature: register the player join listener.
     */
    @Override
    public void initialize() {
        getLifecycleManager().getListenerManager().registerListener(new PlayerLoginListener());
    }

    /**
     * Disable the feature: clear titles if necessary.
     */
    @Override
    public void disable() {
    }
}
