package nl.hauntedmc.serverfeatures.internal.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Centralized scheduler for feature-scoped tasks.
 * Goals:
 * - Track every scheduled task so we can cancel all on feature shutdown.
 * - For one-shot tasks, automatically remove the finished task from tracking.
 * - Be safe when tasks complete on async threads (thread-safe tracking list).
 */
public class FeatureTaskManager {

    private final ServerFeatures plugin;

    /**
     * Thread-safe list because:
     * - One-shot async tasks complete on a non-main thread and remove themselves from this collection.
     * - We may iterate/cancel all on the main thread at shutdown.
     */
    private final List<BukkitTask> scheduledTasks =
            Collections.synchronizedList(new ArrayList<>());

    public FeatureTaskManager(ServerFeatures plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /* ----------------------------------------------------------------------
     * Public API — thin wrappers over generic helpers
     * ---------------------------------------------------------------------- */

    /** Runs a one-time synchronous task immediately. */
    public BukkitTask scheduleOneTimeTask(Runnable task) {
        Objects.requireNonNull(task, "task");
        return scheduleOnce(r -> Bukkit.getScheduler().runTask(plugin, r), task);
    }

    /** Runs a one-time synchronous task with a delay (using Time). */
    public BukkitTask scheduleDelayedTask(Runnable task, BukkitTime delay) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(delay, "delay");
        final long d = clampDelay(delay);
        return scheduleOnce(r -> Bukkit.getScheduler().runTaskLater(plugin, r, d), task);
    }


    /** Runs a repeating synchronous task with no initial delay (using Time for period). */
    public BukkitTask scheduleRepeatingTask(Runnable task, BukkitTime period) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(period, "period");
        final long d = 0L;
        final long p = clampPeriod(period);
        return scheduleRepeating(r -> Bukkit.getScheduler().runTaskTimer(plugin, r, d, p), task);
    }

    /** Runs a repeating synchronous task with no initial delay (using Time for period). */
    public BukkitTask scheduleRepeatingTask(Runnable task, BukkitTime delay, BukkitTime period) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(period, "period");
        final long d = clampDelay(delay);
        final long p = clampPeriod(period);
        return scheduleRepeating(r -> Bukkit.getScheduler().runTaskTimer(plugin, r, d, p), task);
    }

    /** Runs an asynchronous one-time task. */
    public BukkitTask scheduleAsyncTask(Runnable task) {
        Objects.requireNonNull(task, "task");
        return scheduleOnce(r -> Bukkit.getScheduler().runTaskAsynchronously(plugin, r), task);
    }

    /** Runs an asynchronous one-time task with a delay (using Time). */
    public BukkitTask scheduleAsyncDelayedTask(Runnable task, BukkitTime delay) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(delay, "delay");
        final long d = clampDelay(delay);
        return scheduleOnce(r -> Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, r, d), task);
    }

    /** Runs an asynchronous repeating task (using Time). */
    public BukkitTask scheduleAsyncRepeatingTask(Runnable task, BukkitTime delay, BukkitTime period) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(period, "period");
        final long d = clampDelay(delay);
        final long p = clampPeriod(period);
        return scheduleRepeating(r -> Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, r, d, p), task);
    }

    /* ----------------------------------------------------------------------
     * Management
     * ---------------------------------------------------------------------- */

    /** Cancels a task and removes it from tracking. */
    public void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
            scheduledTasks.remove(task);
        }
    }

    /** Returns true if the task is queued (per Bukkit scheduler). */
    public boolean isTaskQueued(int taskID) {
        return Bukkit.getScheduler().isQueued(taskID);
    }

    /** Returns true if the task is currently running (per Bukkit scheduler). */
    public boolean isTaskRunning(int taskID) {
        return Bukkit.getScheduler().isCurrentlyRunning(taskID);
    }

    /**
     * Cancels all scheduled tasks for this feature and clears tracking.
     * Must synchronize while iterating a synchronizedList.
     */
    public void cancelAllTasks() {
        synchronized (scheduledTasks) {
            for (BukkitTask task : scheduledTasks) {
                task.cancel();
            }
            scheduledTasks.clear();
        }
    }

    /** Number of tasks currently tracked. */
    public int getActiveTaskCount() {
        synchronized (scheduledTasks) {
            return scheduledTasks.size();
        }
    }

    /* ----------------------------------------------------------------------
     * Internals — generic helpers to maximize reuse
     * ---------------------------------------------------------------------- */

    /**
     * One-shot scheduler wrapper that:
     * - wraps the runnable to auto-remove when it completes,
     * - tracks the BukkitTask handle,
     * - works for sync/async, now/later (provided by the submitter lambda).
     */
    private BukkitTask scheduleOnce(Function<Runnable, BukkitTask> submitter, Runnable task) {
        AtomicReference<BukkitTask> ref = new AtomicReference<>();
        Runnable wrapped = () -> {
            try {
                task.run();
            } finally {
                scheduledTasks.remove(ref.get());
            }
        };
        BukkitTask bukkitTask = submitter.apply(wrapped);
        ref.set(bukkitTask);
        scheduledTasks.add(bukkitTask);
        return bukkitTask;
    }

    /**
     * Repeating scheduler wrapper that:
     * - does NOT auto-remove (removal happens via cancelTask/cancelAllTasks),
     * - tracks the BukkitTask handle,
     * - works for sync/async, any delay/period (captured by the submitter lambda).
     */
    private BukkitTask scheduleRepeating(Function<Runnable, BukkitTask> submitter, Runnable task) {
        BukkitTask bukkitTask = submitter.apply(task);
        scheduledTasks.add(bukkitTask);
        return bukkitTask;
    }

    /** Clamp delay to >= 0 ticks. */
    private static long clampDelay(BukkitTime t) {
        return Math.max(0L, t.toTicks());
    }

    /** Clamp period to at least 1 tick (Bukkit requirement). */
    private static long clampPeriod(BukkitTime t) {
        return Math.max(1L, t.toTicks());
    }
}
