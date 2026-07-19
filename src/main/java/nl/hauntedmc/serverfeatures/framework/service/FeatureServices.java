package nl.hauntedmc.serverfeatures.framework.service;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;

import java.util.Objects;
import java.util.Optional;

/**
 * Resolves APIs exported by enabled features through DataRegistry's shared service catalog.
 */
public final class FeatureServices {

    private FeatureServices() {
    }

    /**
     * Finds an enabled feature service by API type.
     */
    public static <T> Optional<T> find(ServerFeatures plugin, Class<T> apiType) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(apiType, "apiType");
        return plugin.getDataRegistry()
                .flatMap(dataRegistry -> dataRegistry.featureServices().find(apiType));
    }

    /**
     * Resolves a required enabled feature service by API type.
     *
     * @throws IllegalStateException when DataRegistry is unavailable or the owning feature did not publish the API.
     */
    public static <T> T require(ServerFeatures plugin, Class<T> apiType) {
        return find(plugin, apiType).orElseThrow(() -> missing(apiType));
    }

    /**
     * Finds an enabled feature service by API type.
     */
    public static <T> Optional<T> find(BukkitBaseFeature<?> feature, Class<T> apiType) {
        Objects.requireNonNull(feature, "feature");
        return find(feature.getPlugin(), apiType);
    }

    /**
     * Resolves a required enabled feature service by API type.
     *
     * @throws IllegalStateException when DataRegistry is unavailable or the owning feature did not publish the API.
     */
    public static <T> T require(BukkitBaseFeature<?> feature, Class<T> apiType) {
        Objects.requireNonNull(feature, "feature");
        return require(feature.getPlugin(), apiType);
    }

    private static IllegalStateException missing(Class<?> apiType) {
        Objects.requireNonNull(apiType, "apiType");
        return new IllegalStateException("Feature service is not available: " + apiType.getName() + ".");
    }
}
