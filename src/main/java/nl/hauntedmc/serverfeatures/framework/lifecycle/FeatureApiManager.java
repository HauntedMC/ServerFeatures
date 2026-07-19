package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceHandle;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Tracks feature-owned API/service registrations and detaches them on cleanup.
 */
public class FeatureApiManager {

    private final Map<Class<?>, Object> registeredServices = new LinkedHashMap<>();
    private final Map<Class<?>, FeatureServiceHandle> dataRegistryServices = new LinkedHashMap<>();
    private final String ownerFeature;
    private final Supplier<Optional<DataRegistry>> dataRegistrySupplier;

    public FeatureApiManager(String ownerFeature, Supplier<Optional<DataRegistry>> dataRegistrySupplier) {
        this.ownerFeature = requireText(ownerFeature, "ownerFeature");
        this.dataRegistrySupplier = Objects.requireNonNull(dataRegistrySupplier, "dataRegistrySupplier");
    }

    /**
     * Registers or replaces a feature-owned API for local and DataRegistry-backed discovery.
     */
    public synchronized <T> void registerService(Class<T> type, T instance) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");

        Object previous = registeredServices.get(type);
        FeatureServiceHandle previousHandle = dataRegistryServices.get(type);
        if (previous == instance && previousHandle != null) {
            return;
        }

        FeatureServiceHandle handle = registerWithDataRegistry(type, instance);
        registeredServices.put(type, instance);

        dataRegistryServices.remove(type);
        if (handle != null) {
            dataRegistryServices.put(type, handle);
        }
        if (previousHandle != null) {
            previousHandle.close();
        }
    }

    /**
     * Unregisters one API owned by this feature.
     */
    public synchronized void unregisterService(Class<?> type) {
        Objects.requireNonNull(type, "type");

        registeredServices.remove(type);
        closeDataRegistryHandle(type);
    }

    /**
     * Unregisters all APIs owned by this feature.
     */
    public synchronized void unregisterAllServices() {
        for (var entry : new LinkedHashMap<>(registeredServices).entrySet()) {
            closeDataRegistryHandle(entry.getKey());
        }
        registeredServices.clear();
        dataRegistryServices.values().forEach(FeatureServiceHandle::close);
        dataRegistryServices.clear();
    }

    /**
     * Returns the number of APIs currently owned by this feature.
     */
    public synchronized int getRegisteredServiceCount() {
        return registeredServices.size();
    }

    private void closeDataRegistryHandle(Class<?> type) {
        FeatureServiceHandle handle = dataRegistryServices.remove(type);
        if (handle != null) {
            handle.close();
        }
    }

    private <T> FeatureServiceHandle registerWithDataRegistry(Class<T> type, T instance) {
        return currentDataRegistry()
                .map(dataRegistry -> dataRegistry.featureServices().register(
                        "ServerFeatures",
                        ownerFeature,
                        type,
                        instance
                ))
                .orElse(null);
    }

    private Optional<DataRegistry> currentDataRegistry() {
        Optional<DataRegistry> dataRegistry = dataRegistrySupplier.get();
        return dataRegistry == null ? Optional.empty() : dataRegistry;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
