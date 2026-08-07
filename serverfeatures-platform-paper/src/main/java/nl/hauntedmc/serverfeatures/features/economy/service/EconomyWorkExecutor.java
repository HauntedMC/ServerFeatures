package nl.hauntedmc.serverfeatures.features.economy.service;

import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Bounded Economy-only execution lane for database work and workflow acknowledgements. */
final class EconomyWorkExecutor implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final Duration synchronousTimeout;
    private final Duration shutdownDrain;
    private final AtomicBoolean closed = new AtomicBoolean();

    EconomyWorkExecutor(EconomySettings.Execution settings) {
        Objects.requireNonNull(settings, "settings");
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threads = runnable -> {
            Thread thread = new Thread(runnable, "ServerFeatures-Economy-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        executor = new ThreadPoolExecutor(settings.workers(), settings.workers(), 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(settings.queueCapacity()), threads, new ThreadPoolExecutor.AbortPolicy());
        synchronousTimeout = settings.synchronousTimeout();
        shutdownDrain = settings.shutdownDrain();
    }

    <T> CompletableFuture<T> submit(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        if (closed.get()) {
            return CompletableFuture.failedFuture(new EconomyOverloadedException("Economy is shutting down"));
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    result.complete(work.get());
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException rejected) {
            result.completeExceptionally(new EconomyOverloadedException("Economy is busy; retry shortly", rejected));
        }
        return result;
    }

    <T> T await(Supplier<T> work) {
        try {
            return submit(work).get(synchronousTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException timeout) {
            throw new EconomyOverloadedException("Economy did not complete within " + synchronousTimeout.toMillis() + " ms", timeout);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new EconomyOverloadedException("Economy operation was interrupted", interrupted);
        } catch (java.util.concurrent.ExecutionException execution) {
            Throwable cause = execution.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Economy operation failed", cause);
        }
    }

    int queuedTasks() {
        return executor.getQueue().size();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownDrain.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
