package nl.hauntedmc.serverfeatures.api.feature;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;

import java.util.List;

public interface Feature {

    /**
     * @return Unique name of the feature.
     */
    String getFeatureName();

    /**
     * @return Feature version string.
     */
    String getFeatureVersion();

    /**
     * @return List of required feature dependencies.
     */
    List<String> getDependencies();

    /**
     * @return List of required plugin dependencies.
     */
    List<String> getPluginDependencies();

    /**
     * @return Default configuration values for this feature.
     */
    ConfigMap getDefaultConfig();

    /**
     * @return Default localization messages for this feature.
     */
    MessageMap getDefaultMessages();

    /**
     * Logic to run when the feature is loaded/initialized.
     */
    void initialize();

    /**
     * Logic to run when the feature is disabled/unloaded.
     */
    void disable();
}
