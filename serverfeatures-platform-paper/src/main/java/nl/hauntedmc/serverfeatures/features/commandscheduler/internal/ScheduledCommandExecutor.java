package nl.hauntedmc.serverfeatures.features.commandscheduler.internal;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.commandscheduler.CommandScheduler;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.CommandSchedule;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.random.RandomGenerator;

public final class ScheduledCommandExecutor {

    private final CommandScheduler feature;
    private final RandomGenerator random;
    private final Deque<ScheduleExecutionPlanner.PlannedCommand> queue = new ArrayDeque<>();

    private BukkitTask pendingTask;
    private long generation;
    private boolean active;
    private boolean dispatchingScheduledCommand;

    public ScheduledCommandExecutor(CommandScheduler feature) {
        this(feature, RandomGenerator.getDefault());
    }

    ScheduledCommandExecutor(CommandScheduler feature, RandomGenerator random) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.random = Objects.requireNonNull(random, "random");
    }

    public void enqueue(Collection<CommandSchedule> schedules, String source) {
        List<ScheduleExecutionPlanner.PlannedCommand> planned =
                ScheduleExecutionPlanner.plan(schedules, random);
        if (planned.isEmpty()) {
            return;
        }

        if (feature.shouldLogExecutions()) {
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            planned.forEach(command -> ids.add(command.scheduleId()));
            feature.getLogger().info(
                    "Queued CommandScheduler execution from " + source + ": "
                            + String.join(", ", ids)
            );
        }

        queue.addAll(planned);
        if (!active) {
            active = true;
            runNext(generation);
        }
    }

    public boolean isDispatchingScheduledCommand() {
        return dispatchingScheduledCommand;
    }

    public void shutdown() {
        generation++;
        queue.clear();
        active = false;
        dispatchingScheduledCommand = false;
        if (pendingTask != null) {
            feature.getLifecycleManager().getTaskManager().cancelTask(pendingTask);
            pendingTask = null;
        }
    }

    private void runNext(long token) {
        if (token != generation) {
            return;
        }
        ScheduleExecutionPlanner.PlannedCommand planned = queue.pollFirst();
        if (planned == null) {
            active = false;
            pendingTask = null;
            return;
        }

        if (feature.shouldLogCommands()) {
            feature.getLogger().info(
                    "Executing schedule '" + planned.scheduleId() + "' command "
                            + planned.position() + "/" + planned.total() + ": "
                            + planned.command()
            );
        }

        boolean accepted = false;
        boolean failedWithException = false;
        dispatchingScheduledCommand = true;
        try {
            accepted = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), planned.command());
        } catch (Throwable throwable) {
            failedWithException = true;
            feature.getLogger().log(
                    Level.SEVERE,
                    "Scheduled command from '" + planned.scheduleId() + "' failed.",
                    throwable
            );
        } finally {
            dispatchingScheduledCommand = false;
        }
        if (!accepted && !failedWithException) {
            feature.getLogger().warning(
                    "Scheduled command from '" + planned.scheduleId()
                            + "' was not accepted by the command map."
            );
        }

        if (queue.isEmpty()) {
            active = false;
            pendingTask = null;
            return;
        }
        long delayTicks = feature.getCommandDelayTicks();
        pendingTask = feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                () -> {
                    pendingTask = null;
                    runNext(token);
                },
                BukkitTime.ticks(delayTicks)
        );
    }
}
