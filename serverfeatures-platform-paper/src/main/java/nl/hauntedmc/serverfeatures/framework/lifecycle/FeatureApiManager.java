package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceHandle;
import nl.hauntedmc.serverfeatures.api.feature.FeatureId;
import nl.hauntedmc.serverfeatures.framework.service.CapabilityRegistration;
import nl.hauntedmc.serverfeatures.framework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.serverfeatures.framework.service.FeatureServiceCatalog;
import nl.hauntedmc.serverfeatures.framework.service.InternalServiceRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Stages feature-owned public capabilities, runtime-only services and ingress hooks until the
 * owning feature has initialized and restored reload state.
 *
 * <p>The local/DataRegistry directory remains only for public-service migration during the 3.3
 * refactor. New implementation-only collaboration must use {@link #registerInternalService}.</p>
 */
public class FeatureApiManager {

    private final Map<Class<?>, Object> serviceDefinitions = new LinkedHashMap<>();
    private final Map<Class<?>, ActiveService> activeServices = new LinkedHashMap<>();
    private final Map<Class<?>, Object> internalDefinitions = new LinkedHashMap<>();
    private final Map<Class<?>, CapabilityRegistration> activeInternalServices = new LinkedHashMap<>();
    private final List<Runnable> activationHooks = new ArrayList<>();
    private final String ownerFeature;
    private final FeatureId ownerId;
    private final Supplier<Optional<DataRegistryApi>> dataRegistrySupplier;
    private final FeatureServiceCatalog localCatalog;
    private final DefaultCapabilityRegistry capabilityRegistry;
    private final InternalServiceRegistry internalRegistry;
    private FeatureResourceState state = FeatureResourceState.OPEN;
    private boolean active;

    public FeatureApiManager(String ownerFeature, Supplier<Optional<DataRegistryApi>> dataRegistrySupplier) {
        this(
                ownerFeature,
                dataRegistrySupplier,
                new FeatureServiceCatalog(),
                new DefaultCapabilityRegistry(),
                new InternalServiceRegistry()
        );
    }

    FeatureApiManager(
            String ownerFeature,
            Supplier<Optional<DataRegistryApi>> dataRegistrySupplier,
            FeatureServiceCatalog localCatalog
    ) {
        this(
                ownerFeature,
                dataRegistrySupplier,
                localCatalog,
                new DefaultCapabilityRegistry(),
                new InternalServiceRegistry()
        );
    }

    FeatureApiManager(
            String ownerFeature,
            Supplier<Optional<DataRegistryApi>> dataRegistrySupplier,
            FeatureServiceCatalog localCatalog,
            DefaultCapabilityRegistry capabilityRegistry
    ) {
        this(
                ownerFeature,
                dataRegistrySupplier,
                localCatalog,
                capabilityRegistry,
                new InternalServiceRegistry()
        );
    }

    FeatureApiManager(
            String ownerFeature,
            Supplier<Optional<DataRegistryApi>> dataRegistrySupplier,
            FeatureServiceCatalog localCatalog,
            DefaultCapabilityRegistry capabilityRegistry,
            InternalServiceRegistry internalRegistry
    ) {
        this.ownerFeature = requireText(ownerFeature, "ownerFeature");
        this.ownerId = FeatureId.of(this.ownerFeature);
        this.dataRegistrySupplier = Objects.requireNonNull(dataRegistrySupplier, "dataRegistrySupplier");
        this.localCatalog = Objects.requireNonNull(localCatalog, "localCatalog");
        this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry, "capabilityRegistry");
        this.internalRegistry = Objects.requireNonNull(internalRegistry, "internalRegistry");
    }

    /** Stages or replaces a public service owned by this feature. */
    public synchronized <T> void registerService(Class<T> type, T instance) {
        requireOpen();
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        Object previous = serviceDefinitions.get(type);
        if (previous == instance) {
            return;
        }

        if (!active) {
            serviceDefinitions.put(type, instance);
            return;
        }

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

    /** Stages or replaces a runtime-only collaboration port owned by this feature. */
    public synchronized <T> void registerInternalService(Class<T> type, T instance) {
        requireOpen();
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Internal service contract must be an interface: " + type.getName());
        }
        if (!type.isInstance(instance)) {
            throw new IllegalArgumentException("Service implementation does not implement " + type.getName());
        }

        Object previous = internalDefinitions.get(type);
        if (previous == instance) {
            return;
        }
        internalDefinitions.put(type, instance);
        if (!active) {
            return;
        }

        CapabilityRegistration replacement = previous == null
                ? internalRegistry.register(ownerId, type, instance)
                : internalRegistry.replace(ownerId, type, instance);
        CapabilityRegistration oldRegistration = activeInternalServices.put(type, replacement);
        if (oldRegistration != null) {
            oldRegistration.close();
        }
    }

    /** Registers work that may only begin after reload-state restoration. */
    public synchronized void registerActivationHook(Runnable activationHook) {
        requireOpen();
        if (active) {
            throw new IllegalStateException("Activation hooks cannot be added after activation");
        }
        activationHooks.add(Objects.requireNonNull(activationHook, "activationHook"));
    }

    /** Publishes every staged service atomically from the feature lifecycle's perspective. */
    public synchronized void activateServices() {
        requireOpen();
        if (active) {
            return;
        }

        for (Runnable activationHook : List.copyOf(activationHooks)) {
            activationHook.run();
        }

        Map<Class<?>, ActiveService> published = new LinkedHashMap<>();
        Map<Class<?>, CapabilityRegistration> publishedInternal = new LinkedHashMap<>();
        try {
            for (Map.Entry<Class<?>, Object> entry : serviceDefinitions.entrySet()) {
                published.put(entry.getKey(), publish(entry.getKey(), entry.getValue()));
            }
            for (Map.Entry<Class<?>, Object> entry : internalDefinitions.entrySet()) {
                publishedInternal.put(
                        entry.getKey(),
                        publishInternal(entry.getKey(), entry.getValue())
                );
            }
        } catch (Throwable failure) {
            closeRegistrations(publishedInternal, failure);
            closeServices(published, failure);
            throwUnchecked(failure);
        }
        activeServices.putAll(published);
        activeInternalServices.putAll(publishedInternal);
        active = true;
    }

    /** Withdraws active registrations while retaining definitions for possible re-activation. */
    public synchronized void deactivateServices() {
        active = false;
        Throwable failure = closeRegistrations(activeInternalServices, null);
        activeInternalServices.clear();
        failure = closeServices(activeServices, failure);
        activeServices.clear();
        if (failure != null) {
            throwUnchecked(failure);
        }
    }

    public synchronized void quiesce() {
        if (state == FeatureResourceState.OPEN) {
            state = FeatureResourceState.QUIESCING;
        }
    }

    public synchronized void unregisterService(Class<?> type) {
        Objects.requireNonNull(type, "type");
        serviceDefinitions.remove(type);
        internalDefinitions.remove(type);
        ActiveService service = activeServices.remove(type);
        if (service != null) {
            service.close();
        }
        CapabilityRegistration internal = activeInternalServices.remove(type);
        if (internal != null) {
            internal.close();
        }
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
            internalDefinitions.clear();
            activationHooks.clear();
            state = FeatureResourceState.CLOSED;
        }
        if (failure != null) {
            throwUnchecked(failure);
        }
    }

    public synchronized int getRegisteredServiceCount() {
        return serviceDefinitions.size() + internalDefinitions.size();
    }

    public synchronized int getActivationHookCount() {
        return activationHooks.size();
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized FeatureResourceState state() {
        return state;
    }

    /** Transitional lookup used only while 3.3 consumers move to explicit registries. */
    public synchronized <T> Optional<T> findService(Class<T> type) {
        Optional<T> activeService = localCatalog.find(type);
        if (activeService.isPresent()) {
            return activeService;
        }
        Optional<T> internal = internalRegistry.find(type);
        if (internal.isPresent()) {
            return internal;
        }
        Object staged = serviceDefinitions.get(type);
        if (staged == null) {
            staged = internalDefinitions.get(type);
        }
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
            if (capabilityHandle != null) {
                safelyClose(capabilityHandle, failure);
            }
            if (localRegistered) {
                localCatalog.unregister(ownerFeature, type, instance);
            }
            if (dataRegistryHandle != null) {
                safelyClose(dataRegistryHandle, failure);
            }
            throwUnchecked(failure);
            throw new AssertionError("unreachable");
        }
    }

    private <T> CapabilityRegistration publishInternal(Class<T> type, Object rawInstance) {
        return internalRegistry.register(ownerId, type, type.cast(rawInstance));
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

    private static Throwable closeRegistrations(
            Map<Class<?>, CapabilityRegistration> registrations,
            Throwable failure
    ) {
        List<CapabilityRegistration> values = new ArrayList<>(registrations.values());
        for (int index = values.size() - 1; index >= 0; index--) {
            try {
                values.get(index).close();
            } catch (Throwable closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
        return failure;
    }

    private static Throwable closeServices(Map<Class<?>, ActiveService> services, Throwable failure) {
        List<ActiveService> values = new ArrayList<>(services.values());
        for (int index = values.size() - 1; index >= 0; index--) {
            try {
                values.get(index).close();
            } catch (Throwable closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
        return failure;
    }

    private static void safelyClose(AutoCloseable closeable, Throwable failure) {
        try {
            closeable.close();
        } catch (Throwable closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
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

        private ActiveService(
                Class<?> type,
                Object instance,
                FeatureServiceHandle dataRegistryHandle,
                CapabilityRegistration capabilityHandle
        ) {
            this.type = type;
            this.instance = instance;
            this.dataRegistryHandle = dataRegistryHandle;
            this.capabilityHandle = capabilityHandle;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            Throwable failure = null;
            if (capabilityHandle != null) {
                try {
                    capabilityHandle.close();
                } catch (Throwable throwable) {
                    failure = throwable;
                }
            }
            try {
                localCatalog.unregister(ownerFeature, type, instance);
            } catch (Throwable throwable) {
                if (failure == null) {
                    failure = throwable;
                } else {
                    failure.addSuppressed(throwable);
                }
            }
            if (dataRegistryHandle != null) {
                try {
                    dataRegistryHandle.close();
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
    }
}
