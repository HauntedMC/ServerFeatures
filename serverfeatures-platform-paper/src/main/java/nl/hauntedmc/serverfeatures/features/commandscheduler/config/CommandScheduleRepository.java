package nl.hauntedmc.serverfeatures.features.commandscheduler.config;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigView;
import nl.hauntedmc.serverfeatures.features.commandscheduler.CommandScheduler;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.CommandSchedule;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ExecutionMode;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ScheduleParser;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ScheduleTrigger;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ScheduleType;
import nl.hauntedmc.serverfeatures.framework.config.FeatureStoragePaths;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Persistent schedule store backed by {@code local/commandscheduler.yml}.
 */
public final class CommandScheduleRepository extends ConfigView {

    private static final String FILE_NAME = "commandscheduler.yml";

    private final CommandScheduler feature;
    private Map<String, Object> preservedInvalidEntries = Map.of();
    private boolean schedulesWritable = true;

    public CommandScheduleRepository(CommandScheduler feature) {
        super(
                feature.getPlugin().getConfigService().open(
                        FeatureStoragePaths.localDataPath(FILE_NAME),
                        false
                ),
                ""
        );
        this.feature = feature;
        if (node("schedules").isNull()) {
            put("schedules", new LinkedHashMap<>());
        }
    }

    public LoadResult load() {
        Map<String, CommandSchedule> loaded = new LinkedHashMap<>();
        LinkedHashMap<String, Object> invalidEntries = new LinkedHashMap<>();
        int invalid = 0;
        ConfigNode schedulesNode = node("schedules");
        if (schedulesNode.isNull()) {
            schedulesWritable = true;
            preservedInvalidEntries = Map.of();
            return new LoadResult(Map.of(), 0);
        }
        if (!(schedulesNode.raw() instanceof Map<?, ?>)) {
            schedulesWritable = false;
            feature.getLogger().warning(
                    "Ignoring local/commandscheduler.yml because 'schedules' is not a map. "
                            + "In-game schedule changes are blocked until the file is corrected and reloaded."
            );
            preservedInvalidEntries = Map.of();
            return new LoadResult(Map.of(), 1);
        }

        schedulesWritable = true;
        for (Map.Entry<String, ConfigNode> entry : schedulesNode.children().entrySet()) {
            String rawId = entry.getKey();
            try {
                CommandSchedule schedule = parseSchedule(rawId, entry.getValue());
                if (loaded.putIfAbsent(schedule.id(), schedule) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate schedule id after case normalization: " + rawId
                    );
                }
            } catch (RuntimeException exception) {
                invalid++;
                invalidEntries.put(rawId, entry.getValue().raw());
                feature.getLogger().warning(
                        "Ignoring invalid schedule '" + rawId + "': " + rootMessage(exception)
                );
            }
        }
        preservedInvalidEntries = Collections.unmodifiableMap(invalidEntries);
        return new LoadResult(immutableOrdered(loaded.values()), invalid);
    }

    public void reload() {
        file.reload();
    }

    public void save(Collection<CommandSchedule> schedules) {
        save(schedules, Set.of());
    }

    public void save(
            Collection<CommandSchedule> schedules,
            Collection<String> removedScheduleIds
    ) {
        if (!schedulesWritable) {
            throw new IllegalStateException(
                    "Cannot update local/commandscheduler.yml while 'schedules' is not a map"
            );
        }

        SaveData saveData = buildSaveData(
                schedules,
                preservedInvalidEntries,
                removedScheduleIds
        );
        put("schedules", saveData.serialized());
        // Update preservation state only after the durable write succeeds.
        preservedInvalidEntries = saveData.retainedInvalidEntries();
    }

    static SaveData buildSaveData(
            Collection<CommandSchedule> schedules,
            Map<String, Object> invalidEntries,
            Collection<String> removedScheduleIds
    ) {
        List<CommandSchedule> ordered = schedules.stream()
                .sorted(Comparator.comparing(CommandSchedule::id))
                .toList();
        Set<String> validIds = ordered.stream()
                .map(CommandSchedule::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> removedIds = removedScheduleIds.stream()
                .map(CommandSchedule::normalizeId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        LinkedHashMap<String, Object> retainedInvalid = new LinkedHashMap<>();
        invalidEntries.forEach((rawId, value) -> {
            String normalizedId = normalizeOptionalId(rawId);
            if (normalizedId == null
                    || (!validIds.contains(normalizedId) && !removedIds.contains(normalizedId))) {
                retainedInvalid.put(rawId, value);
            }
        });

        LinkedHashMap<String, Object> serialized = new LinkedHashMap<>(retainedInvalid);
        ordered.forEach(schedule -> serialized.put(schedule.id(), serializeSchedule(schedule)));
        return new SaveData(
                Collections.unmodifiableMap(serialized),
                Collections.unmodifiableMap(retainedInvalid)
        );
    }

    static CommandSchedule parseSchedule(String rawId, ConfigNode node) {
        if (!(node.raw() instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Schedule entry must be a map");
        }
        String id = CommandSchedule.normalizeId(rawId);
        boolean enabled = parseEnabled(node.get("enabled"));
        ExecutionMode mode = ScheduleParser.parseMode(
                node.get("mode").as(String.class, "sequence")
        );

        ConfigNode triggerNode = node.get("trigger");
        if (!(triggerNode.raw() instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("trigger must be a map");
        }
        ScheduleType type = ScheduleParser.parseType(
                triggerNode.get("type").as(String.class, null)
        );
        LocalTime time = ScheduleParser.parseTime(
                triggerNode.get("time").as(String.class, null)
        );
        ScheduleTrigger trigger;
        if (type == ScheduleType.DAILY) {
            trigger = ScheduleTrigger.daily(time);
        } else {
            DayOfWeek day = ScheduleParser.parseDay(
                    triggerNode.get("day").as(String.class, null)
            );
            trigger = ScheduleTrigger.weekly(day, time);
        }

        ConfigNode commandsNode = node.get("commands");
        Object rawCommands = commandsNode.raw();
        if (rawCommands != null && !(rawCommands instanceof List<?>)) {
            throw new IllegalArgumentException("commands must be a list");
        }
        List<String> commands = new ArrayList<>();
        if (rawCommands instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                Object command = list.get(index);
                if (!(command instanceof String string)) {
                    throw new IllegalArgumentException(
                            "commands[" + index + "] must be a string"
                    );
                }
                commands.add(string);
            }
        }
        if (enabled && commands.isEmpty()) {
            throw new IllegalArgumentException("enabled schedules require at least one command");
        }
        return new CommandSchedule(id, enabled, trigger, mode, commands);
    }

    static Map<String, Object> serializeSchedule(CommandSchedule schedule) {
        LinkedHashMap<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("type", schedule.trigger().type().name().toLowerCase(Locale.ROOT));
        if (schedule.trigger().type() == ScheduleType.WEEKLY) {
            trigger.put("day", schedule.trigger().day().name());
        }
        trigger.put("time", schedule.trigger().time().toString());

        LinkedHashMap<String, Object> serialized = new LinkedHashMap<>();
        serialized.put("enabled", schedule.enabled());
        serialized.put("trigger", trigger);
        serialized.put("mode", schedule.mode().name().toLowerCase(Locale.ROOT));
        serialized.put("commands", schedule.commands());
        return serialized;
    }

    private static boolean parseEnabled(ConfigNode node) {
        Object raw = node.raw();
        if (raw == null) {
            return false;
        }
        if (raw instanceof Boolean enabled) {
            return enabled;
        }
        throw new IllegalArgumentException("enabled must be a boolean");
    }

    private static String normalizeOptionalId(String rawId) {
        try {
            return CommandSchedule.normalizeId(rawId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Map<String, CommandSchedule> immutableOrdered(
            Collection<CommandSchedule> schedules
    ) {
        LinkedHashMap<String, CommandSchedule> ordered = new LinkedHashMap<>();
        schedules.stream()
                .sorted(Comparator.comparing(CommandSchedule::id))
                .forEach(schedule -> ordered.put(schedule.id(), schedule));
        return Collections.unmodifiableMap(ordered);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }

    static record SaveData(
            Map<String, Object> serialized,
            Map<String, Object> retainedInvalidEntries
    ) {
    }

    public record LoadResult(Map<String, CommandSchedule> schedules, int invalidCount) {
    }
}
