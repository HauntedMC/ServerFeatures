package nl.hauntedmc.serverfeatures.features.commandscheduler.internal;

import nl.hauntedmc.serverfeatures.features.commandscheduler.model.CommandSchedule;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ExecutionMode;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ScheduleTrigger;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScheduleExecutionPlannerTest {

    @Test
    void sequenceSchedulesRemainContiguousAndAreOrderedById() {
        CommandSchedule second = schedule(
                "second",
                ExecutionMode.SEQUENCE,
                List.of("say second-1", "say second-2")
        );
        CommandSchedule first = schedule(
                "first",
                ExecutionMode.SEQUENCE,
                List.of("say first-1", "say first-2")
        );

        List<ScheduleExecutionPlanner.PlannedCommand> planned =
                ScheduleExecutionPlanner.plan(List.of(second, first), mock(RandomGenerator.class));

        assertEquals(
                List.of("say first-1", "say first-2", "say second-1", "say second-2"),
                planned.stream().map(ScheduleExecutionPlanner.PlannedCommand::command).toList()
        );
        assertEquals(
                List.of("first", "first", "second", "second"),
                planned.stream().map(ScheduleExecutionPlanner.PlannedCommand::scheduleId).toList()
        );
    }

    @Test
    void randomScheduleSelectsExactlyOneConfiguredCommand() {
        RandomGenerator random = mock(RandomGenerator.class);
        when(random.nextInt(3)).thenReturn(1);
        CommandSchedule schedule = schedule(
                "event",
                ExecutionMode.RANDOM,
                List.of("event one", "event two", "event three")
        );

        List<ScheduleExecutionPlanner.PlannedCommand> planned =
                ScheduleExecutionPlanner.plan(List.of(schedule), random);

        assertEquals(1, planned.size());
        assertEquals("event two", planned.getFirst().command());
        assertEquals(2, planned.getFirst().position());
        assertEquals(3, planned.getFirst().total());
    }

    private CommandSchedule schedule(
            String id,
            ExecutionMode mode,
            List<String> commands
    ) {
        return new CommandSchedule(
                id,
                true,
                ScheduleTrigger.daily(LocalTime.of(20, 0)),
                mode,
                commands
        );
    }
}
