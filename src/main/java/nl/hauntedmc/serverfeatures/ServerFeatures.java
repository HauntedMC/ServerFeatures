package nl.hauntedmc.serverfeatures;

import nl.hauntedmc.serverfeatures.common.BaseFeature;
import nl.hauntedmc.serverfeatures.config.ConfigHandler;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;

import java.util.*;
import java.util.logging.Level;

public class ServerFeatures extends JavaPlugin {

    private final List<BaseFeature<?>> loadedFeatures = new ArrayList<>();
    private ConfigHandler configHandler;

    @Override
    public void onEnable() {
        configHandler = new ConfigHandler(this);

        List<Class<? extends BaseFeature<?>>> featureClasses = findFeatures();
        Set<String> registeredFeatureNames = new HashSet<>();

        for (Class<? extends BaseFeature<?>> featureClass : featureClasses) {
            try {
                BaseFeature<?> feature = featureClass.getDeclaredConstructor(JavaPlugin.class).newInstance(this);
                String featureName = feature.getFeatureName();
                registeredFeatureNames.add(featureName);

                // Inject default config settings BEFORE checking if enabled
                configHandler.registerFeature(featureName);
                configHandler.injectFeatureDefaults(featureName, feature.getDefaultConfig());

                // Now check if enabled
                if (configHandler.isFeatureEnabled(featureName)) {
                    loadedFeatures.add(feature);
                    getLogger().info("Feature loaded: " + featureName);
                    feature.initialize();
                }

            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to load feature: " + featureClass.getName(), e);
            }
        }

        configHandler.cleanupUnusedFeatures(registeredFeatureNames);
    }

    @Override
    public void onDisable() {
        getLogger().info("ServerFeatures is shutting down...");
    }

    /**
     * Automatically finds all classes extending BaseFeature<?> in the features package.
     */
    private List<Class<? extends BaseFeature<?>>> findFeatures() {
        List<Class<? extends BaseFeature<?>>> featureClasses = new ArrayList<>();

        try (ScanResult scanResult = new ClassGraph()
                .enableClassInfo()
                .acceptPackages("nl.hauntedmc.serverfeatures.features")
                .scan()) {

            scanResult.getSubclasses(BaseFeature.class.getName()).forEach(classInfo -> {
                try {
                    Class<?> clazz = Class.forName(classInfo.getName());
                    if (BaseFeature.class.isAssignableFrom(clazz)) {
                        featureClasses.add((Class<? extends BaseFeature<?>>) clazz);
                    }
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            });
        }

        getLogger().info("Discovered features: " + featureClasses);
        return featureClasses;
    }

    public List<BaseFeature<?>> getLoadedFeatures() {
        return loadedFeatures;
    }
}
