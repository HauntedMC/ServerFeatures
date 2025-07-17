package nl.hauntedmc.serverfeatures.features.whitelist;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.whitelist.listener.PlayerLoginListener;
import nl.hauntedmc.serverfeatures.features.whitelist.meta.Meta;

public class Whitelist extends BukkitBaseFeature<Meta> {

    public Whitelist(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    /**
     * Returns default config values for the Titles feature.
     */
    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
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
