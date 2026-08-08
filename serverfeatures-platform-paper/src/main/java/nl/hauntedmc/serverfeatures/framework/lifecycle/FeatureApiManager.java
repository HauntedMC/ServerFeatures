package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.serverfeatures.api.feature.FeatureId;
import nl.hauntedmc.serverfeatures.framework.service.CapabilityRegistration;
import nl.hauntedmc.serverfeatures.framework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.serverfeatures.framework.service.InternalServiceRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Stages feature capabilities and ingress hooks until post-restoration activation. */
public class FeatureApiManager {

    private enum RegistryKind { PUBLIC, INTERNAL }

    private final Map<Class<?>, ServiceDefinition> serviceDefinitions = new LinkedHashMap<>();
    private final Map<Class<?>, CapabilityRegistration> activeRegistrations = new LinkedHashMap<>();
    private final List<Runnable> activationHooks = new ArrayList<>();
    private DefaultCapabilityRegistry registry;
    private InternalServiceRegistry internalRegistry;
    private FeatureId owner;
    private boolean active;
    private FeatureResourceState state = FeatureResourceState.OPEN;

    public synchronized void bindRegistry(
            DefaultCapabilityRegistry registry,
            InternalServiceRegistry internalRegistry,
            String ownerFeature
    ) {
        requireOpen();
        if (!serviceDefinitions.isEmpty() || !activeRegistrations.isEmpty() || !activationHooks.isEmpty()) {
            throw new IllegalStateException("Feature API manager cannot be rebound after resources were registered");
        }
        this.registry = Objects.requireNonNull(registry, "registry");
        this.internalRegistry = Objects.requireNonNull(internalRegistry, "internalRegistry");
        this.owner = FeatureId.of(requireText(ownerFeature, "ownerFeature"));
    }

    public synchronized <T> void registerInternalService(Class<T> type, T instance) {
        register(type, instance, RegistryKind.INTERNAL);
    }

    public synchronized <T> void registerService(Class<T> type, T instance) {
        register(type, instance, RegistryKind.PUBLIC);
    }

    /** Registers ingress work that may only start after reload state restoration. */
    public synchronized void registerActivationHook(Runnable activationHook) {
        requireOpen();
        if (active) {
            throw new IllegalStateException("Activation hooks cannot be added after feature activation");
        }
        activationHooks.add(Objects.requireNonNull(activationHook, "activationHook"));
    }

    public synchronized void activateServices() {
        requireOpen();
        requireBound();
        if (active) {
            return;
        }

        for (Runnable activationHook : List.copyOf(activationHooks)) {
            activationHook.run();
        }

        Map<Class<?>, CapabilityRegistration> published = new LinkedHashMap<>();
        try {
            for (Map.Entry<Class<?>, ServiceDefinition> entry : serviceDefinitions.entrySet()) {
                published.put(entry.getKey(), publish(entry.getKey(), entry.getValue()));
            }
        } catch (Throwable activationFailure) {
            closeRegistrations(published, activationFailure);
            throwUnchecked(activationFailure);
        }
        activeRegistrations.putAll(published);
        active = true;
    }

    public synchronized void deactivateServices() {
        active = false;
        Throwable failure = closeRegistrations(activeRegistrations, null);
        activeRegistrations.clear();
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
        CapabilityRegistration registration = activeRegistrations.remove(type);
        if (registration != null) {
            registration.close();
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
            activationHooks.clear();
            state = FeatureResourceState.CLOSED;
        }
        if (failure != null) {
            throwUnchecked(failure);
        }
    }

    public synchronized int getRegisteredServiceCount() {
        return serviceDefinitions.size();
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

    private <T> void register(Class<T> type, T instance, RegistryKind kind) {
        requireOpen();
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        requireBound();

        ServiceDefinition previous = serviceDefinitions.get(type);
        if (previous != null && previous.instance() == instance && previous.kind() == kind) {
            return;
        }

        ServiceDefinition replacement = new ServiceDefinition(kind, instance);
        if (!active) {
            serviceDefinitions.put(type, replacement);
            return;
        }
        if (previous != null && previous.kind() != kind) {
            throw new IllegalStateException(
                    "Active service cannot change registry kind without deactivation: " + type.getName()
            );
        }

        CapabilityRegistration replacementRegistration = previous == null
                ? publish(type, replacement)
                : replace(type, replacement);
        CapabilityRegistration previousRegistration = activeRegistrations.put(type, replacementRegistration);
        serviceDefinitions.put(type, replacement);
        if (previousRegistration != null) {
            previousRegistration.close();
        }
    }

    private CapabilityRegistration publish(Class<?> type, ServiceDefinition definition) {
        return switch (definition.kind()) {
            case PUBLIC -> publishPublic(type, definition.instance());
            case INTERNAL -> publishInternal(type, definition.instance());
        };
    }

    private CapabilityRegistration replace(Class<?> type, ServiceDefinition definition) {
        return switch (definition.kind()) {
            case PUBLIC -> replacePublic(type, definition.instance());
            case INTERNAL -> replaceInternal(type, definition.instance());
        };
    }

    private <T> CapabilityRegistration publishPublic(Class<T> type, Object instance) {
        return registry.register(owner, type, type.cast(instance));
    }

    private <T> CapabilityRegistration publishInternal(Class<T> type, Object instance) {
        return internalRegistry.register(owner, type, type.cast(instance));
    }

    private <T> CapabilityRegistration replacePublic(Class<T> type, Object instance) {
        return registry.replace(owner, type, type.cast(instance));
    }

    private <T> CapabilityRegistration replaceInternal(Class<T> type, Object instance) {
        return internalRegistry.replace(owner, type, type.cast(instance));
    }

    private void requireBound() {
        if (registry == null || internalRegistry == null || owner == null) {
            throw new IllegalStateException("Feature API manager is not bound to capability registries");
        }
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

    private record ServiceDefinition(RegistryKind kind, Object instance) {
    }
}
