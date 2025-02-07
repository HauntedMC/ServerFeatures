package nl.hauntedmc.serverfeatures.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class FeatureTaskManager {

    private final ServerFeatures plugin;
    private final List<BukkitTask> scheduledTasks = new ArrayList<>();

    public FeatureTaskManager(ServerFeatures plugin) {
        this.plugin = plugin;
    }

    /**
     * Runs a one-time synchronous task immediately.
     */
    public void scheduleOneTimeTask(Runnable task) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTask(plugin, task);
        scheduledTasks.add(bukkitTask);
    }

    /**
     * Runs a one-time synchronous task with a delay.
     */
    public void scheduleDelayedTask(Runnable task, long delay) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        scheduledTasks.add(bukkitTask);
    }

    /**
     * Runs a repeating synchronous task with no initial delay.
     */
    public void scheduleRepeatingTask(Runnable task, long period) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, 0L, period);
        scheduledTasks.add(bukkitTask);
    }

    /**
     * Runs a repeating synchronous task with an initial delay.
     */
    public void scheduleDelayedRepeatingTask(Runnable task, long delay, long period) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        scheduledTasks.add(bukkitTask);
    }

    /**
     * Runs an asynchronous one-time task.
     */
    public void scheduleAsyncTask(Runnable task) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        scheduledTasks.add(bukkitTask);
    }

    /**
     * Runs an asynchronous repeating task.
     */
    public void scheduleAsyncRepeatingTask(Runnable task, long delay, long period) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
        scheduledTasks.add(bukkitTask);
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
}
