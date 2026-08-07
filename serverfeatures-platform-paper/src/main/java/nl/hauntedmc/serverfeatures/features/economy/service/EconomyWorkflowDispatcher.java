package nl.hauntedmc.serverfeatures.features.economy.service;

import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowHandler;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowRegistration;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.persistence.EconomyRepository;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Delivers committed workflow events at least once without holding database locks during handlers. */
final class EconomyWorkflowDispatcher {
    private static final int BATCH_SIZE = 16;

    private final Economy feature;
    private final EconomyRepository repository;
    private final BooleanSupplier closed;
    private final ConcurrentHashMap<String, EconomyWorkflowHandler> handlers = new ConcurrentHashMap<>();
    private final AtomicBoolean dispatching = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();
    private final String owner;

    EconomyWorkflowDispatcher(Economy feature, EconomyRepository repository, BooleanSupplier closed) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.closed = Objects.requireNonNull(closed, "closed");
        this.owner = "paper:" + feature.settings().serverKey() + ":" + java.util.UUID.randomUUID();
    }

    void start() {
        if (running.compareAndSet(false, true)) {
            feature.getLifecycleManager().getTaskManager().scheduleAsyncRepeatingTask(
                    this::dispatch, BukkitTime.seconds(1), BukkitTime.seconds(1));
        }
    }

    EconomyWorkflowRegistration register(String eventType, EconomyWorkflowHandler handler) {
        String normalized = normalizeEventType(eventType);
        Objects.requireNonNull(handler, "handler");
        EconomyWorkflowHandler existing = handlers.putIfAbsent(normalized, handler);
        if (existing != null) {
            throw new IllegalStateException("An Economy workflow handler is already registered for " + normalized);
        }
        return () -> handlers.remove(normalized, handler);
    }

    void close() {
        running.set(false);
        handlers.clear();
    }

    private void dispatch() {
        if (!running.get() || closed.getAsBoolean() || handlers.isEmpty() || !dispatching.compareAndSet(false, true)) {
            return;
        }
        try {
            repository.claimWorkflows(handlers.keySet(), owner, BATCH_SIZE).forEach(this::deliver);
        } catch (RuntimeException failure) {
            feature.getLogger().warning("Could not claim Economy workflow events: " + EconomyFailure.rootMessage(failure));
        } finally {
            dispatching.set(false);
        }
    }

    private void deliver(EconomyRepository.WorkflowClaim claim) {
        EconomyWorkflowHandler handler = handlers.get(claim.event().eventType());
        if (handler == null) {
            release(claim, "No handler is registered for " + claim.event().eventType());
            return;
        }
        CompletionStage<Void> completion;
        try {
            completion = Objects.requireNonNull(handler.fulfil(claim.event()), "Workflow handler returned no completion stage");
        } catch (RuntimeException failure) {
            release(claim, EconomyFailure.rootMessage(failure));
            return;
        }
        completion.whenComplete((ignored, failure) -> {
            if (!running.get() || closed.getAsBoolean()) {
                return;
            }
            try {
                feature.getLifecycleManager().getTaskManager().runAsync(() -> {
                    try {
                        if (failure == null) {
                            repository.acknowledgeWorkflow(claim.eventId(), claim.owner());
                        } else {
                            repository.releaseWorkflow(claim.eventId(), claim.owner(), EconomyFailure.rootMessage(failure));
                        }
                    } catch (RuntimeException databaseFailure) {
                        feature.getLogger().warning("Could not acknowledge Economy workflow " + claim.eventId()
                                + ": " + EconomyFailure.rootMessage(databaseFailure));
                    }
                });
            } catch (RuntimeException schedulingFailure) {
                feature.getLogger().warning("Could not schedule Economy workflow acknowledgement " + claim.eventId()
                        + ": " + EconomyFailure.rootMessage(schedulingFailure));
            }
        });
    }

    private void release(EconomyRepository.WorkflowClaim claim, String message) {
        try {
            feature.getLifecycleManager().getTaskManager().runAsync(() -> {
                try {
                    repository.releaseWorkflow(claim.eventId(), claim.owner(), message);
                } catch (RuntimeException failure) {
                    feature.getLogger().warning("Could not release Economy workflow " + claim.eventId()
                            + ": " + EconomyFailure.rootMessage(failure));
                }
            });
        } catch (RuntimeException schedulingFailure) {
            feature.getLogger().warning("Could not schedule Economy workflow release " + claim.eventId()
                    + ": " + EconomyFailure.rootMessage(schedulingFailure));
        }
    }

    private static String normalizeEventType(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9_.:-]{0,63}")) {
            throw new IllegalArgumentException("eventType contains unsupported characters");
        }
        return normalized;
    }
}
