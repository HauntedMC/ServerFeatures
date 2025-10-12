package nl.hauntedmc.serverfeatures.features.instaskull;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.instaskull.listener.SkullBreakListener;
import nl.hauntedmc.serverfeatures.features.instaskull.meta.Meta;

public class InstaSkull extends BukkitBaseFeature<Meta> {

    public InstaSkull(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        getLifecycleManager().getListenerManager().registerListener(new SkullBreakListener());
    }

    @Override
    public void disable() {
    }

}
