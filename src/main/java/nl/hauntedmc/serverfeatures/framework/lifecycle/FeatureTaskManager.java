package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Centralized scheduler for feature-scoped tasks.
 *
 * <p>Every submitted task is tracked so feature shutdown can cancel outstanding work. One-shot tasks
 * remove themselves after completion, including tasks that complete before the scheduler returns their
 * handle.</p>
 */
public class FeatureTaskManager {

    private final Plugin plugin;
    private final List<BukkitTask> scheduledTasks = Collections.synchronizedList(new ArrayList<>());
    private final Map<BukkitTask, CompletableFuture<?>> taskFutures = new ConcurrentHashMap<>();

    public FeatureTaskManager(ServerFeatures plugin) {
        this((Plugin) plugin);
    }

    public FeatureTaskManager(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public BukkitTask scheduleOneTimeTask(Runnable task) {
        Objects.requireNonNull(task, "task");
        return scheduleOnce(runnable -> Bukkit.getScheduler().runTask(plugin, runnable), task);
    }

    public BukkitTask scheduleDelayedTask(Runnable task, BukkitTime delay) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(delay, "delay");
        long delayTicks = clampDelay(delay);
        return scheduleOnce(
                runnable -> Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks),
                task
        );
    }

    public BukkitTask scheduleRepeatingTask(Runnable task, BukkitTime period) {
        return scheduleRepeatingTask(task, BukkitTime.ticks(0L), period);
    }

    public BukkitTask scheduleRepeatingTask(Runnable task, BukkitTime delay, BukkitTime period) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(period, "period");
        long delayTicks = clampDelay(delay);
        long periodTicks = clampPeriod(period);
        return scheduleRepeating(
                runnable -> Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks),
                task
        );
    }

    public BukkitTask scheduleAsyncTask(Runnable task) {
        Objects.requireNonNull(task, "task");
        return scheduleOnce(
                runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable),
                task
        );
    }

    /**
     * Runs feature-scoped asynchronous work and returns a future that is cancelled with the feature.
     */
    public CompletableFuture<Void> runAsync(Runnable task) {
        Objects.requireNonNull(task, "task");
        return supplyAsync(() -> {
            task.run();
            return null;
        });
    }

    /**
     * Runs a blocking or computational supplier on Bukkit's asynchronous scheduler.
     *
     * <p>This deliberately avoids both the Bukkit main thread and the JVM common pool. The returned
     * future is cancelled when its tracked Bukkit task is cancelled.</p>
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        CompletableFuture<T> result = new CompletableFuture<>();

        try {
            BukkitTask task = scheduleAsyncTask(() -> {
                if (result.isCancelled()) {
                    return;
                }
                try {
                    result.complete(supplier.get());
                } catch (Throwable throwable) {
                    result.completeExceptionally(throwable);
                }
            });

            taskFutures.put(task, result);
            result.whenComplete((ignored, throwable) -> taskFutures.remove(task, result));
        } catch (RuntimeException exception) {
            result.completeExceptionally(exception);
        }

        return result;
    }

    public BukkitTask scheduleAsyncDelayedTask(Runnable task, BukkitTime delay) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(delay, "delay");
        long delayTicks = clampDelay(delay);
        return scheduleOnce(
                runnable -> Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delayTicks),
                task
        );
    }

    public BukkitTask scheduleAsyncRepeatingTask(Runnable task, BukkitTime delay, BukkitTime period) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(period, "period");
        long delayTicks = clampDelay(delay);
        long periodTicks = clampPeriod(period);
        return scheduleRepeating(
                runnable -> Bukkit.getScheduler().runTaskTimerAsynchronously(
                        plugin,
                        runnable,
                        delayTicks,
                        periodTicks
                ),
                task
        );
    }

    public void cancelTask(BukkitTask task) {
        if (task == null) {
            return;
        }

        CompletableFuture<?> future = taskFutures.remove(task);
        if (future != null) {
            future.cancel(false);
        }
        task.cancel();
        scheduledTasks.remove(task);
    }

    public boolean isTaskQueued(int taskId) {
        return Bukkit.getScheduler().isQueued(taskId);
    }

    public boolean isTaskRunning(int taskId) {
        return Bukkit.getScheduler().isCurrentlyRunning(taskId);
    }

    public void cancelAllTasks() {
        synchronized (scheduledTasks) {
            for (BukkitTask task : scheduledTasks) {
                CompletableFuture<?> future = taskFutures.remove(task);
                if (future != null) {
                    future.cancel(false);
                }
                task.cancel();
            }
            scheduledTasks.clear();
        }
        taskFutures.clear();
    }

    public int getActiveTaskCount() {
        synchronized (scheduledTasks) {
            return scheduledTasks.size();
        }
    }

    private BukkitTask scheduleOnce(Function<Runnable, BukkitTask> submitter, Runnable task) {
        AtomicReference<BukkitTask> taskReference = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        Runnable wrapped = () -> {
            try {
                task.run();
            } finally {
                completed.set(true);
                BukkitTask handle = taskReference.get();
                if (handle != null) {
                    scheduledTasks.remove(handle);
                    taskFutures.remove(handle);
                }
            }
        };

        BukkitTask bukkitTask = submitter.apply(wrapped);
        taskReference.set(bukkitTask);
        scheduledTasks.add(bukkitTask);

        if (completed.get()) {
            scheduledTasks.remove(bukkitTask);
            taskFutures.remove(bukkitTask);
        }

        return bukkitTask;
    }

    private BukkitTask scheduleRepeating(Function<Runnable, BukkitTask> submitter, Runnable task) {
        BukkitTask bukkitTask = submitter.apply(task);
        scheduledTasks.add(bukkitTask);
        return bukkitTask;
    }

    static long clampDelay(BukkitTime time) {
        return Math.max(0L, time.toTicks());
    }

    static long clampPeriod(BukkitTime time) {
        return Math.max(1L, time.toTicks());
    }
}
