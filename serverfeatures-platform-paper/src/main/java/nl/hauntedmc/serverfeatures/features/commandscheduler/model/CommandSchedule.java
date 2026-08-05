package nl.hauntedmc.serverfeatures.features.commandscheduler.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record CommandSchedule(
        String id,
        boolean enabled,
        ScheduleTrigger trigger,
        ExecutionMode mode,
        List<String> commands
) {

    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_-]+");

    public CommandSchedule {
        id = normalizeId(id);
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(mode, "mode");
        commands = normalizeCommands(commands);
    }

    public static String normalizeId(String raw) {
        String normalized = Objects.requireNonNull(raw, "id").trim().toLowerCase(Locale.ROOT);
        if (!VALID_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Schedule id must match [a-z0-9_-]+: " + raw
            );
        }
        return normalized;
    }

    public static String normalizeCommand(String raw) {
        String command = Objects.requireNonNull(raw, "command").trim();
        while (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (command.isEmpty()) {
            throw new IllegalArgumentException("Command must not be blank");
        }
        return command;
    }

    private static List<String> normalizeCommands(List<String> rawCommands) {
        if (rawCommands == null || rawCommands.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>(rawCommands.size());
        for (String command : rawCommands) {
            normalized.add(normalizeCommand(command));
        }
        return List.copyOf(normalized);
    }

    public CommandSchedule withEnabled(boolean value) {
        return new CommandSchedule(id, value, trigger, mode, commands);
    }

    public CommandSchedule withTrigger(ScheduleTrigger value) {
        return new CommandSchedule(id, enabled, value, mode, commands);
    }

    public CommandSchedule withMode(ExecutionMode value) {
        return new CommandSchedule(id, enabled, trigger, value, commands);
    }

    public CommandSchedule withCommands(List<String> value) {
        return new CommandSchedule(id, enabled, trigger, mode, value);
    }
}
