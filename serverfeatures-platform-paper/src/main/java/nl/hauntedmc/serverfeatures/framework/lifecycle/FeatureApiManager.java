package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceHandle;
import nl.hauntedmc.serverfeatures.api.feature.FeatureId;
import nl.hauntedmc.serverfeatures.framework.service.CapabilityRegistration;
import nl.hauntedmc.serverfeatures.framework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.serverfeatures.framework.service.FeatureServiceCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Stages feature-owned services during initialization and publishes them only when the feature is
 * ready to become active. Public serverfeatures-api interfaces are additionally exposed through
 * the generation-safe capability registry; the legacy local/DataRegistry directory is retained
 * temporarily while feature-local integrations are migrated to explicit 3.3 ports.
 */
public class FeatureApiManager {

    private final Map<Class<?>, Object> serviceDefinitions = new LinkedHashMap<>();
    private final Map<Class<?>, ActiveService> activeServices = new LinkedHashMap<>();
    private final List<Runnable> activationHooks = new ArrayList<>();
    private final String ownerFeature;
    private final FeatureId ownerId;
    private final Supplier<Optional<DataRegistryApi>> dataRegistrySupplier;
    private final FeatureServiceCatalog localCatalog;
    private final DefaultCapabilityRegistry capabilityRegistry;
    private FeatureResourceState state = FeatureResourceState.OPEN;
    private boolean active;

    public FeatureApiManager(String ownerFeature, Supplier<Optional<DataRegistryApi>> dataRegistrySupplier) {
        this(ownerFeature, dataRegistrySupplier, new FeatureServiceCatalog(), new DefaultCapabilityRegistry());
    }

    FeatureApiManager(
            String ownerFeature,
            Supplier<Optional<DataRegistryApi>> dataRegistrySupplier,
            FeatureServiceCatalog localCatalog
    ) {
        this(ownerFeature, dataRegistrySupplier, localCatalog, new DefaultCapabilityRegistry());
    }

    FeatureApiManager(
            String ownerFeature,
            Supplier<Optional<DataRegistryApi>> dataRegistrySupplier,
            FeatureServiceCatalog localCatalog,
            DefaultCapabilityRegistry capabilityRegistry
    ) {
        this.ownerFeature = requireText(ownerFeature, "ownerFeature");
        this.ownerId = FeatureId.of(this.ownerFeature);
        this.dataRegistrySupplier = Objects.requireNonNull(dataRegistrySupplier, "dataRegistrySupplier");
        this.localCatalog = Objects.requireNonNull(localCatalog, "localCatalog");
        this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry, "capabilityRegistry");
    }

    /** Stages or replaces a service owned by this feature. */
    public synchronized <T> void registerService(Class<T> type, T instance) {
        requireOpen();
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        Object previous = serviceDefinitions.get(type);
        if (previous == instance) return;

        if (!active) {
            serviceDefinitions.put(type, instance);
            return;
        }

        // Runtime service replacement is uncommon and correctness matters more than a tiny
        // availability gap: withdraw the old generation, replace the definition, then republish
        // the complete set transactionally.
        Map<Class<?>, Object> previousDefinitions = new LinkedHashMap<>(serviceDefinitions);
        deactivateServices();
        serviceDefinitions.put(type, instance);
        try {
            activateServices();
        } catch (Throwable failure) {
            serviceDefinitions.clear();
            serviceDefinitions.putAll(previousDefinitions);
            try {
                activateServices();
            } catch (Throwable rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throwUnchecked(failure);
        }
    }

    /** Registers work that may only begin after reload-state restoration. */
    public synchronized void registerActivationHook(Runnable activationHook) {
        requireOpen();
        if (active) throw new IllegalStateException("Activation hooks cannot be added after activation");
        activationHooks.add(Objects.requireNonNull(activationHook, "activationHook"));
    }

    /** Publishes every staged service atomically from the feature lifecycle's perspective. */
    public synchronized void activateServices() {
        requireOpen();
        if (active) return;

        for (Runnable activationHook : List.copyOf(activationHooks)) {
            activationHook.run();
        }

        Map<Class<?>, ActiveService> published = new LinkedHashMap<>();
        try {
            for (Map.Entry<Class<?>, Object> entry : serviceDefinitions.entrySet()) {
                published.put(entry.getKey(), publish(entry.getKey(), entry.getValue()));
            }
        } catch (Throwable failure) {
            closeServices(published, failure);
            throwUnchecked(failure);
        }
        activeServices.putAll(published);
        active = true;
    }

    /** Withdraws active registrations while retaining definitions for a possible re-activation. */
    public synchronized void deactivateServices() {
        active = false;
        Throwable failure = closeServices(activeServices, null);
        activeServices.clear();
        if (failure != null) throwUnchecked(failure);
    }

    public synchronized void quiesce() {
        if (state == FeatureResourceState.OPEN) state = FeatureResourceState.QUIESCING;
    }

    public synchronized void unregisterService(Class<?> type) {
        Objects.requireNonNull(type, "type");
        serviceDefinitions.remove(type);
        ActiveService service = activeServices.remove(type);
        if (service != null) service.close();
    }

    public synchronized void unregisterAllServices() {
        quiesce();
        Throwable failure = null;
        try {
            deactivateServices();
        } catch (Throwable deactivationFailure) {
            failure = deactivationFailure;
        } finally {
            serviceDefinitions.clear();
            activationHooks.clear();
            state = FeatureResourceState.CLOSED;
        }
        if (failure != null) throwUnchecked(failure);
    }

    public synchronized int getRegisteredServiceCount() { return serviceDefinitions.size(); }
    public synchronized int getActivationHookCount() { return activationHooks.size(); }
    public synchronized boolean isActive() { return active; }
    public synchronized FeatureResourceState state() { return state; }

    /** Returns an active shared service, or this manager's own staged definition before activation. */
    public synchronized <T> Optional<T> findService(Class<T> type) {
        Optional<T> activeService = localCatalog.find(type);
        if (activeService.isPresent()) return activeService;
        Object staged = serviceDefinitions.get(type);
        return staged == null ? Optional.empty() : Optional.of(type.cast(staged));
    }

    private <T> ActiveService publish(Class<T> type, Object rawInstance) {
        T instance = type.cast(rawInstance);
        FeatureServiceHandle dataRegistryHandle = null;
        CapabilityRegistration capabilityHandle = null;
        boolean localRegistered = false;
        try {
            dataRegistryHandle = registerWithDataRegistry(type, instance);
            localCatalog.register(ownerFeature, type, instance);
            localRegistered = true;
            if (isPublicCapability(type)) {
                capabilityHandle = capabilityRegistry.register(ownerId, type, instance);
            }
            return new ActiveService(type, instance, dataRegistryHandle, capabilityHandle);
        } catch (Throwable failure) {
            if (capabilityHandle != null) safelyClose(capabilityHandle, failure);
            if (localRegistered) localCatalog.unregister(ownerFeature, type, instance);
            if (dataRegistryHandle != null) safelyClose(dataRegistryHandle, failure);
            throwUnchecked(failure);
            throw new AssertionError("unreachable");
        }
    }

    private <T> FeatureServiceHandle registerWithDataRegistry(Class<T> type, T instance) {
        return currentDataRegistry()
                .map(dataRegistry -> dataRegistry.featureServices().register(
                        "ServerFeatures", ownerFeature, type, instance))
                .orElse(null);
    }

    private Optional<DataRegistryApi> currentDataRegistry() {
        Optional<DataRegistryApi> current = dataRegistrySupplier.get();
        return current == null ? Optional.empty() : current;
    }

    private static boolean isPublicCapability(Class<?> type) {
        return type.isInterface() && type.getPackageName().startsWith("nl.hauntedmc.serverfeatures.api.");
    }

    private void requireOpen() {
        if (state != FeatureResourceState.OPEN) {
            throw new IllegalStateException("Feature API manager is " + state);
        }
    }

    private static Throwable closeServices(Map<Class<?>, ActiveService> services, Throwable failure) {
        List<ActiveService> values = new ArrayList<>(services.values());
        for (int index = values.size() - 1; index >= 0; index--) {
            try {
                values.get(index).close();
            } catch (Throwable closeFailure) {
                if (failure == null) failure = closeFailure;
                else failure.addSuppressed(closeFailure);
            }
        }
        return failure;
    }

    private static void safelyClose(AutoCloseable closeable, Throwable failure) {
        try { closeable.close(); } catch (Throwable closeFailure) { failure.addSuppressed(closeFailure); }
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(fieldName + " must not be blank");
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable throwable) throws E {
        throw (E) throwable;
    }

    private final class ActiveService implements AutoCloseable {
        private final Class<?> type;
        private final Object instance;
        private final FeatureServiceHandle dataRegistryHandle;
        private final CapabilityRegistration capabilityHandle;
        private boolean closed;

        private ActiveService(Class<?> type, Object instance, FeatureServiceHandle dataRegistryHandle,
                              CapabilityRegistration capabilityHandle) {
            this.type = type;
            this.instance = instance;
            this.dataRegistryHandle = dataRegistryHandle;
            this.capabilityHandle = capabilityHandle;
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            Throwable failure = null;
            if (capabilityHandle != null) {
                try { capabilityHandle.close(); } catch (Throwable throwable) { failure = throwable; }
            }
            try { localCatalog.unregister(ownerFeature, type, instance); }
            catch (Throwable throwable) {
                if (failure == null) failure = throwable; else failure.addSuppressed(throwable);
            }
            if (dataRegistryHandle != null) {
                try { dataRegistryHandle.close(); }
                catch (Throwable throwable) {
                    if (failure == null) failure = throwable; else failure.addSuppressed(throwable);
                }
            }
            if (failure != null) throwUnchecked(failure);
        }
    }
}
