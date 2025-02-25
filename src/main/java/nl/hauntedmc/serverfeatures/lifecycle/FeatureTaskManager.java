package nl.hauntedmc.serverfeatures.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class FeatureTaskManager {

    private final ServerFeatures plugin;
    private final List<BukkitTask> scheduledTasks = new ArrayList<>();

    public FeatureTaskManager(ServerFeatures plugin) {
        this.plugin = plugin;
    }

    /**
     * Runs a one-time synchronous task immediately.
     */
    public BukkitTask scheduleOneTimeTask(Runnable task) {
        AtomicReference<BukkitTask> taskRef = new AtomicReference<>();
        Runnable wrappedTask = () -> {
            try {
                task.run();
            } finally {
                scheduledTasks.remove(taskRef.get());
            }
        };
        BukkitTask bukkitTask = Bukkit.getScheduler().runTask(plugin, wrappedTask);
        taskRef.set(bukkitTask);
        scheduledTasks.add(bukkitTask);
        return bukkitTask;
    }

    /**
     * Runs a one-time synchronous task with a delay.
     */
    public BukkitTask scheduleDelayedTask(Runnable task, long delay) {
        AtomicReference<BukkitTask> taskRef = new AtomicReference<>();
        Runnable wrappedTask = () -> {
            try {
                task.run();
            } finally {
                scheduledTasks.remove(taskRef.get());
            }
        };
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, wrappedTask, delay);
        taskRef.set(bukkitTask);
        scheduledTasks.add(bukkitTask);
        return bukkitTask;
    }

    /**
     * Runs a repeating synchronous task with no initial delay.
     */
    public BukkitTask scheduleRepeatingTask(Runnable task, long period) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, 0L, period);
        scheduledTasks.add(bukkitTask);
        return bukkitTask;
    }

    /**
     * Runs a repeating synchronous task with an initial delay.
     */
    public BukkitTask scheduleDelayedRepeatingTask(Runnable task, long delay, long period) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        scheduledTasks.add(bukkitTask);
        return bukkitTask;
    }

    /**
     * Runs an asynchronous one-time task.
     */
    public BukkitTask scheduleAsyncTask(Runnable task) {
        AtomicReference<BukkitTask> taskRef = new AtomicReference<>();
        Runnable wrappedTask = () -> {
            try {
                task.run();
            } finally {
                scheduledTasks.remove(taskRef.get());
            }
        };
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, wrappedTask);
        taskRef.set(bukkitTask);
        scheduledTasks.add(bukkitTask);
        return bukkitTask;
    }

    /**
     * Runs an asynchronous repeating task.
     */
    public BukkitTask scheduleAsyncRepeatingTask(Runnable task, long delay, long period) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
        scheduledTasks.add(bukkitTask);
        return bukkitTask;
    }

    /**
     * Must be used to cancel a task.
     */
    public void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
            scheduledTasks.remove(task);
        }
    }

    /**
     * Cancels all scheduled tasks.
     */
    public void cancelAllTasks() {
        for (BukkitTask task : scheduledTasks) {
            task.cancel();
        }
        scheduledTasks.clear();
    }

    /**
     * Get the number of active tasks
     */
    public int getActiveTaskCount() {
        return scheduledTasks.size();
    }
}
