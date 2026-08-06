package nl.hauntedmc.serverfeatures.features.economy.service;

import nl.hauntedmc.serverfeatures.features.economy.Economy;
import org.bukkit.Bukkit;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Safely moves Economy's Bukkit-facing work onto the primary server thread.
 *
 * <p>Repository and messaging callbacks may run on arbitrary worker threads. Keeping this
 * boundary in one class prevents accidental Bukkit access from those callbacks.</p>
 */
final class EconomyMainThreadExecutor {
    private final Economy feature;
    private final BooleanSupplier closed;

    EconomyMainThreadExecutor(Economy feature, BooleanSupplier closed) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.closed = Objects.requireNonNull(closed, "closed");
    }

    /** Runs the task now when possible, otherwise schedules it on Bukkit's primary thread. */
    void execute(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (closed.getAsBoolean()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
            if (!closed.getAsBoolean()) {
                task.run();
            }
        });
    }
}
