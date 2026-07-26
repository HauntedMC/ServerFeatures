package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceHandle;
import nl.hauntedmc.serverfeatures.framework.service.FeatureServiceCatalog;

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
    private final Supplier<Optional<DataRegistryApi>> dataRegistrySupplier;
    private final FeatureServiceCatalog localCatalog;

    public FeatureApiManager(String ownerFeature, Supplier<Optional<DataRegistryApi>> dataRegistrySupplier) {
        this(ownerFeature, dataRegistrySupplier, new FeatureServiceCatalog());
    }

    FeatureApiManager(
            String ownerFeature,
            Supplier<Optional<DataRegistryApi>> dataRegistrySupplier,
            FeatureServiceCatalog localCatalog
    ) {
        this.ownerFeature = requireText(ownerFeature, "ownerFeature");
        this.dataRegistrySupplier = Objects.requireNonNull(dataRegistrySupplier, "dataRegistrySupplier");
        this.localCatalog = Objects.requireNonNull(localCatalog, "localCatalog");
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
        try {
            localCatalog.register(ownerFeature, type, instance);
        } catch (Throwable throwable) {
            if (handle != null) {
                handle.close();
            }
            throw throwable;
        }
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

        Object service = registeredServices.remove(type);
        if (service != null) {
            localCatalog.unregister(ownerFeature, type, service);
        }
        closeDataRegistryHandle(type);
    }

    /**
     * Unregisters all APIs owned by this feature.
     */
    public synchronized void unregisterAllServices() {
        Map<Class<?>, Object> services = new LinkedHashMap<>(registeredServices);
        Map<Class<?>, FeatureServiceHandle> handles = new LinkedHashMap<>(dataRegistryServices);
        registeredServices.clear();
        dataRegistryServices.clear();

        services.forEach((type, service) -> localCatalog.unregister(ownerFeature, type, service));
        Throwable failure = null;
        for (FeatureServiceHandle handle : handles.values()) {
            try {
                handle.close();
            } catch (Throwable throwable) {
                if (failure == null) {
                    failure = throwable;
                } else {
                    failure.addSuppressed(throwable);
                }
            }
        }
        if (failure != null) {
            throwUnchecked(failure);
        }
    }

    /**
     * Returns the number of APIs currently owned by this feature.
     */
    public synchronized int getRegisteredServiceCount() {
        return registeredServices.size();
    }

    public synchronized <T> Optional<T> findService(Class<T> type) {
        return localCatalog.find(type);
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

    private Optional<DataRegistryApi> currentDataRegistry() {
        Optional<DataRegistryApi> dataRegistry = dataRegistrySupplier.get();
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

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable throwable) throws E {
        throw (E) throwable;
    }
}
