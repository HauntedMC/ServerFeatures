package nl.hauntedmc.serverfeatures.framework.service;

import nl.hauntedmc.serverfeatures.api.feature.FeatureId;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runtime-only registry for implementation collaboration that must never become public API. */
public final class InternalServiceRegistry {
    private record Provider(FeatureId owner, Object instance) {
    }

    private final ConcurrentHashMap<Class<?>, Provider> providers = new ConcurrentHashMap<>();

    public <T> CapabilityRegistration register(FeatureId owner, Class<T> type, T instance) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        validate(type, instance);

        Provider provider = new Provider(owner, instance);
        providers.compute(type, (ignored, current) -> {
            if (current != null) {
                throw new IllegalStateException(type.getName() + " is already provided by " + current.owner());
            }
            return provider;
        });
        return registration(type, provider);
    }

    public <T> CapabilityRegistration replace(FeatureId owner, Class<T> type, T instance) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        validate(type, instance);

        Provider replacement = new Provider(owner, instance);
        providers.compute(type, (ignored, current) -> {
            if (current == null) {
                throw new IllegalStateException(type.getName() + " is not currently registered");
            }
            if (!current.owner().equals(owner)) {
                throw new IllegalStateException(type.getName() + " is provided by another owner: " + current.owner());
            }
            return replacement;
        });
        return registration(type, replacement);
    }

    private CapabilityRegistration registration(Class<?> type, Provider provider) {
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (closed.compareAndSet(false, true)) {
                providers.remove(type, provider);
            }
        };
    }

    public <T> Optional<T> find(Class<T> type) {
        Provider provider = providers.get(Objects.requireNonNull(type, "type"));
        return provider == null ? Optional.empty() : Optional.of(type.cast(provider.instance()));
    }

    public <T> T require(Class<T> type) {
        return find(type).orElseThrow(() -> new IllegalStateException(
                "Internal feature service is unavailable: " + type.getName()
        ));
    }

    public Optional<FeatureId> owner(Class<?> type) {
        Provider provider = providers.get(Objects.requireNonNull(type, "type"));
        return provider == null ? Optional.empty() : Optional.of(provider.owner());
    }

    private static <T> void validate(Class<T> type, T instance) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Internal service contract must be an interface: " + type.getName());
        }
        if (!type.isInstance(instance)) {
            throw new IllegalArgumentException("Service implementation does not implement " + type.getName());
        }
    }
}
