package nl.hauntedmc.serverfeatures.features.instaskull;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BaseFeature;
import nl.hauntedmc.serverfeatures.features.instaskull.listener.SkullBreakListener;
import nl.hauntedmc.serverfeatures.features.instaskull.meta.Meta;
import nl.hauntedmc.serverfeatures.localization.MessageMap;

import java.util.HashMap;
import java.util.Map;

public class InstaSkull extends BaseFeature<Meta> {

    public InstaSkull(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        getLifecycleManager().getListenerManager().registerListener(new SkullBreakListener(this));
    }

    @Override
    public void disable() {
    }

}
