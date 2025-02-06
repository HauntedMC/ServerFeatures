package nl.hauntedmc.serverfeatures.features;

import nl.hauntedmc.serverfeatures.common.BaseFeature;
import org.bukkit.plugin.java.JavaPlugin;

public class FeatureFactory {

    public static BaseFeature<?> createFeature(Class<? extends BaseFeature<?>> featureClass, JavaPlugin plugin) {
        try {
            return featureClass.getDeclaredConstructor(JavaPlugin.class).newInstance(plugin);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to instantiate feature: " + featureClass.getSimpleName() + " - " + e.getMessage());
            return null;
        }
    }
}
