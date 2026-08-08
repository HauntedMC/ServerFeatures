package nl.hauntedmc.serverfeatures.framework.service;

import nl.hauntedmc.serverfeatures.api.ApiFailureCode;
import nl.hauntedmc.serverfeatures.api.ApiOperationException;
import nl.hauntedmc.serverfeatures.api.feature.FeatureId;
import nl.hauntedmc.serverfeatures.api.service.CapabilityListener;
import nl.hauntedmc.serverfeatures.api.service.CapabilityRef;
import nl.hauntedmc.serverfeatures.api.service.CapabilityRegistry;
import nl.hauntedmc.serverfeatures.api.service.CapabilityUnavailableException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Thread-safe ServerFeatures-owned capability registry with generation-safe replacement. */
public final class DefaultCapabilityRegistry implements CapabilityRegistry {
    private static final Duration DEFAULT_DRAIN_TIMEOUT = Duration.ofSeconds(5);

    private static final class Provider {
        private final FeatureId owner;
        private final Object instance;
        private final long generation;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition drained = lock.newCondition();
        private final Set<AsyncInvocation> asynchronousInvocations = ConcurrentHashMap.newKeySet();
        private final Duration drainTimeout;
        private boolean accepting = true;
        private int inFlight;

        private Provider(FeatureId owner, Object instance, long generation, Duration drainTimeout) {
            this.owner = owner;
            this.instance = instance;
            this.generation = generation;
            this.drainTimeout = drainTimeout;
        }

        private InvocationLease tryAcquire() {
            lock.lock();
            try {
                if (!accepting) return null;
                inFlight++;
                return new InvocationLease(this);
            } finally { lock.unlock(); }
        }

        private void release() {
            lock.lock();
            try {
                inFlight--;
                if (inFlight == 0) drained.signalAll();
            } finally { lock.unlock(); }
        }

        private boolean track(AsyncInvocation invocation) {
            lock.lock();
            try {
                if (!accepting) return false;
                asynchronousInvocations.add(invocation);
                return true;
            } finally { lock.unlock(); }
        }

        private void complete(AsyncInvocation invocation) { asynchronousInvocations.remove(invocation); }

        private void completeAsync(AsyncInvocation invocation, Object value, Throwable failure) {
            lock.lock();
            try {
                if (accepting) invocation.complete(value, failure); else invocation.invalidate();
            } finally { lock.unlock(); }
        }

        private Set<AsyncInvocation> stopAccepting() {
            lock.lock();
            try {
                accepting = false;
                return Set.copyOf(asynchronousInvocations);
            } finally { lock.unlock(); }
        }

        private ApiOperationException stopAndAwaitDrain(Set<AsyncInvocation> pending) {
            pending.forEach(AsyncInvocation::invalidate);
            boolean interrupted = false;
            lock.lock();
            try {
                long remainingNanos = drainTimeout.toNanos();
                while (inFlight > 0) {
                    if (remainingNanos <= 0) {
                        return new ApiOperationException(ApiFailureCode.TIMEOUT,
                                "Timed out draining capability provider " + owner + " with " + inFlight
                                        + " synchronous invocation(s) still running");
                    }
                    try { remainingNanos = drained.awaitNanos(remainingNanos); }
                    catch (InterruptedException ignored) { interrupted = true; }
                }
            } finally {
                lock.unlock();
                if (interrupted) Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private static final class InvocationLease implements AutoCloseable {
        private final Provider provider;
        private final AtomicBoolean closed = new AtomicBoolean();
        private InvocationLease(Provider provider) { this.provider = provider; }
        private Object instance() { return provider.instance; }
        private Provider provider() { return provider; }
        @Override public void close() { if (closed.compareAndSet(false, true)) provider.release(); }
    }

    private static final class AsyncInvocation {
        private final Class<?> type;
        private final Provider provider;
        private final InvocationLease lease;
        private final CompletableFuture<Object> result = new CompletableFuture<>();
        private final AtomicBoolean completed = new AtomicBoolean();

        private AsyncInvocation(Class<?> type, InvocationLease lease) {
            this.type = type;
            this.lease = lease;
            this.provider = lease.provider();
        }

        private CompletionStage<Object> result() { return result; }

        private void complete(Object value, Throwable failure) {
            if (!completed.compareAndSet(false, true)) return;
            try {
                if (failure == null) result.complete(value); else result.completeExceptionally(failure);
            } finally {
                provider.complete(this);
                lease.close();
            }
        }

        private void invalidate() {
            complete(null, new ApiOperationException(ApiFailureCode.PROVIDER_RELOADED,
                    "ServerFeatures capability provider reloaded: " + type.getName()));
        }
    }

    private record Withdrawal(Provider provider, Set<AsyncInvocation> pending) { }

    private final ConcurrentHashMap<Class<?>, Provider> providers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, CapabilityRef<?>> references = new ConcurrentHashMap<>();
    private final AtomicLong generations = new AtomicLong();
    private final CopyOnWriteArrayList<CapabilityListener> listeners = new CopyOnWriteArrayList<>();
    private final Duration drainTimeout;

    public DefaultCapabilityRegistry() { this(DEFAULT_DRAIN_TIMEOUT); }

    DefaultCapabilityRegistry(Duration drainTimeout) {
        this.drainTimeout = Objects.requireNonNull(drainTimeout, "drainTimeout");
        if (drainTimeout.isNegative() || drainTimeout.isZero()) {
            throw new IllegalArgumentException("drainTimeout must be positive");
        }
    }

    public <T> CapabilityRegistration register(FeatureId owner, Class<T> type, T instance) {
        validate(owner, type, instance);
        Provider provider = new Provider(owner, instance, generations.incrementAndGet(), drainTimeout);
        configureProviderGeneration(instance, provider.generation);
        providers.compute(type, (ignored, current) -> {
            if (current != null) {
                throw new IllegalStateException("Capability " + type.getName() + " is already provided by " + current.owner);
            }
            return provider;
        });
        notifyAvailable(type, provider.generation);
        return registration(type, provider);
    }

    public <T> CapabilityRegistration replace(FeatureId owner, Class<T> type, T instance) {
        validate(owner, type, instance);
        Provider replacement = new Provider(owner, instance, generations.incrementAndGet(), drainTimeout);
        configureProviderGeneration(instance, replacement.generation);
        Withdrawal[] withdrawal = new Withdrawal[1];
        providers.compute(type, (ignored, current) -> {
            if (current == null) throw new IllegalStateException("Capability " + type.getName() + " is not registered");
            if (!current.owner.equals(owner)) {
                throw new IllegalStateException("Capability " + type.getName() + " is provided by " + current.owner);
            }
            withdrawal[0] = new Withdrawal(current, current.stopAccepting());
            return replacement;
        });
        withdrawal[0].provider().stopAndAwaitDrain(withdrawal[0].pending());
        notifyReplaced(type, withdrawal[0].provider().generation, replacement.generation);
        return registration(type, replacement);
    }

    private <T> void validate(FeatureId owner, Class<T> type, T instance) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        validateCapabilityType(type);
        if (!type.isInstance(instance)) {
            throw new IllegalArgumentException("Capability implementation does not implement " + type.getName());
        }
    }

    private CapabilityRegistration registration(Class<?> type, Provider provider) {
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (!closed.compareAndSet(false, true)) return;
            Withdrawal[] withdrawal = new Withdrawal[1];
            providers.compute(type, (ignored, current) -> {
                if (current != provider) return current;
                withdrawal[0] = new Withdrawal(provider, provider.stopAccepting());
                return null;
            });
            if (withdrawal[0] == null) return;
            try {
                ApiOperationException timeout = withdrawal[0].provider().stopAndAwaitDrain(withdrawal[0].pending());
                if (timeout != null) throw timeout;
            } finally {
                notifyUnavailable(type, withdrawal[0].provider().generation);
            }
        };
    }

    @Override
    public <T> CapabilityRef<T> reference(Class<T> type) {
        Objects.requireNonNull(type, "type");
        validateCapabilityType(type);
        CapabilityRef<?> reference = references.computeIfAbsent(type, DefaultCapabilityRef::new);
        return typeSafeReference(type, reference);
    }

    @Override public Set<Class<?>> availableTypes() { return Set.copyOf(new LinkedHashSet<>(providers.keySet())); }

    @Override
    public AutoCloseable subscribe(CapabilityListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    public Optional<FeatureId> owner(Class<?> type) {
        Provider provider = providers.get(Objects.requireNonNull(type, "type"));
        return provider == null ? Optional.empty() : Optional.of(provider.owner);
    }

    private void notifyAvailable(Class<?> type, long generation) {
        listeners.forEach(listener -> safely(() -> listener.available(type, generation)));
    }
    private void notifyUnavailable(Class<?> type, long generation) {
        listeners.forEach(listener -> safely(() -> listener.unavailable(type, generation)));
    }
    private void notifyReplaced(Class<?> type, long previous, long next) {
        listeners.forEach(listener -> safely(() -> listener.replaced(type, previous, next)));
    }
    private static void safely(Runnable callback) { try { callback.run(); } catch (RuntimeException ignored) { } }

    private static void configureProviderGeneration(Object instance, long generation) {
        if (instance instanceof CapabilityProviderGenerationAware aware) aware.providerGeneration(generation);
    }

    private static void validateCapabilityType(Class<?> type) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Capability contract must be an interface: " + type.getName());
        }
        if (!type.getPackageName().startsWith("nl.hauntedmc.serverfeatures.api.")) {
            throw new IllegalArgumentException("Capability contract must come from serverfeatures-api: " + type.getName());
        }
        Class<?> canonicalType;
        try {
            canonicalType = Class.forName(type.getName(), false, CapabilityRegistry.class.getClassLoader());
        } catch (ClassNotFoundException missingApiType) {
            throw new IllegalArgumentException("Capability is not part of the active serverfeatures-api: " + type.getName(),
                    missingApiType);
        }
        if (canonicalType != type) {
            throw new IllegalArgumentException("Capability was loaded from a duplicate serverfeatures-api: " + type.getName());
        }
    }

    private InvocationLease acquire(Class<?> type) {
        while (true) {
            Provider provider = providers.get(type);
            if (provider == null) throw new CapabilityUnavailableException(type);
            InvocationLease lease = provider.tryAcquire();
            if (lease != null) return lease;
        }
    }

    private <T> Optional<T> resolveProxy(DefaultCapabilityRef<T> reference) {
        return providers.containsKey(reference.type) ? Optional.of(reference.proxy) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static <T> CapabilityRef<T> typeSafeReference(Class<T> type, CapabilityRef<?> reference) {
        if (reference.type() != type) throw new IllegalStateException("Capability reference type mismatch");
        return (CapabilityRef<T>) reference;
    }

    private final class DefaultCapabilityRef<T> implements CapabilityRef<T> {
        private final Class<T> type;
        private final T proxy;

        private DefaultCapabilityRef(Class<T> type) {
            this.type = type;
            this.proxy = type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, this::invoke));
        }

        @Override public Class<T> type() { return type; }
        @Override public Optional<T> get() { return resolveProxy(this); }
        @Override public OptionalLong generation() {
            Provider provider = providers.get(type);
            return provider == null ? OptionalLong.empty() : OptionalLong.of(provider.generation);
        }

        private Object invoke(Object proxyInstance, Method method, Object[] arguments) throws Throwable {
            if (method.getDeclaringClass() == Object.class) return invokeObjectMethod(proxyInstance, method, arguments);
            InvocationLease lease = acquire(type);
            boolean async = false;
            try {
                Object result;
                try { result = method.invoke(lease.instance(), arguments); }
                catch (InvocationTargetException failure) { throw failure.getCause(); }
                if (result instanceof CompletionStage<?> stage) {
                    AsyncInvocation invocation = new AsyncInvocation(type, lease);
                    if (lease.provider().track(invocation)) {
                        stage.whenComplete((value, failure) -> lease.provider().completeAsync(invocation, value, failure));
                    } else {
                        invocation.invalidate();
                    }
                    async = true;
                    return invocation.result();
                }
                return result;
            } finally {
                if (!async) lease.close();
            }
        }

        private Object invokeObjectMethod(Object proxyInstance, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "equals" -> proxyInstance == arguments[0];
                case "hashCode" -> System.identityHashCode(proxyInstance);
                case "toString" -> "CapabilityRefProxy[" + type.getName() + "]";
                default -> throw new IllegalStateException("Unsupported Object method: " + method);
            };
        }
    }
}
