package nl.hauntedmc.serverfeatures.features.commandscheduler.config;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.CommandSchedule;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ExecutionMode;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ScheduleType;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandScheduleRepositoryTest {

    @Test
    void parsesAndCanonicalizesWeeklySchedule() {
        ConfigNode node = ConfigNode.ofRaw(Map.of(
                "enabled", true,
                "trigger", Map.of(
                        "type", "weekly",
                        "day", "vrijdag",
                        "time", "21:00"
                ),
                "mode", "random",
                "commands", List.of("/event start spleef", " event start trivia ")
        ), "schedules.friday_event");

        CommandSchedule schedule = CommandScheduleRepository.parseSchedule("Friday_Event", node);

        assertEquals("friday_event", schedule.id());
        assertTrue(schedule.enabled());
        assertEquals(ScheduleType.WEEKLY, schedule.trigger().type());
        assertEquals(DayOfWeek.FRIDAY, schedule.trigger().day());
        assertEquals(LocalTime.of(21, 0), schedule.trigger().time());
        assertEquals(ExecutionMode.RANDOM, schedule.mode());
        assertEquals(List.of("event start spleef", "event start trivia"), schedule.commands());
    }

    @Test
    void disabledScheduleMayBeStoredWithoutCommands() {
        ConfigNode node = ConfigNode.ofRaw(Map.of(
                "enabled", false,
                "trigger", Map.of("type", "daily", "time", "20:00"),
                "mode", "sequence",
                "commands", List.of()
        ), "schedules.new_schedule");

        CommandSchedule schedule = CommandScheduleRepository.parseSchedule("new_schedule", node);

        assertFalse(schedule.enabled());
        assertTrue(schedule.commands().isEmpty());
    }

    @Test
    void enabledScheduleWithoutCommandsIsRejected() {
        ConfigNode node = ConfigNode.ofRaw(Map.of(
                "enabled", true,
                "trigger", Map.of("type", "daily", "time", "20:00"),
                "mode", "sequence",
                "commands", List.of()
        ), "schedules.invalid");

        assertThrows(
                IllegalArgumentException.class,
                () -> CommandScheduleRepository.parseSchedule("invalid", node)
        );
    }

    @Test
    void serializationRoundTripsWithoutLosingOrderOrMode() {
        CommandSchedule original = new CommandSchedule(
                "daily_rewards",
                true,
                new nl.hauntedmc.serverfeatures.features.commandscheduler.model.ScheduleTrigger(
                        ScheduleType.DAILY,
                        null,
                        LocalTime.of(20, 0)
                ),
                ExecutionMode.SEQUENCE,
                List.of("say one", "say two")
        );

        CommandSchedule restored = CommandScheduleRepository.parseSchedule(
                original.id(),
                ConfigNode.ofRaw(
                        CommandScheduleRepository.serializeSchedule(original),
                        "schedules." + original.id()
                )
        );

        assertEquals(original, restored);
    }
}
