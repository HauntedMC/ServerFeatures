package nl.hauntedmc.serverfeatures.features.commandscheduler.internal;

import nl.hauntedmc.serverfeatures.features.commandscheduler.model.CommandSchedule;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ExecutionMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class ScheduleExecutionPlanner {

    private ScheduleExecutionPlanner() {
    }

    public static List<PlannedCommand> plan(
            Collection<CommandSchedule> schedules,
            RandomGenerator random
    ) {
        Objects.requireNonNull(schedules, "schedules");
        Objects.requireNonNull(random, "random");

        List<CommandSchedule> ordered = schedules.stream()
                .sorted(Comparator.comparing(CommandSchedule::id))
                .toList();
        List<PlannedCommand> planned = new ArrayList<>();
        for (CommandSchedule schedule : ordered) {
            if (schedule.commands().isEmpty()) {
                continue;
            }
            if (schedule.mode() == ExecutionMode.RANDOM) {
                int selectedIndex = random.nextInt(schedule.commands().size());
                planned.add(new PlannedCommand(
                        schedule.id(),
                        schedule.commands().get(selectedIndex),
                        selectedIndex + 1,
                        schedule.commands().size()
                ));
                continue;
            }
            for (int index = 0; index < schedule.commands().size(); index++) {
                planned.add(new PlannedCommand(
                        schedule.id(),
                        schedule.commands().get(index),
                        index + 1,
                        schedule.commands().size()
                ));
            }
        }
        return List.copyOf(planned);
    }

    public record PlannedCommand(String scheduleId, String command, int position, int total) {
    }
}
