package nl.hauntedmc.serverfeatures.features.instaskull;

import nl.hauntedmc.serverfeatures.common.BaseFeature;
import nl.hauntedmc.serverfeatures.features.instaskull.meta.Meta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class InstaSkull extends BaseFeature<Meta> {

    public InstaSkull(JavaPlugin plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        return defaults;
    }

    @Override
    public void initialize() {
    }
}
