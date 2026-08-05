package nl.hauntedmc.serverfeatures.features.commandscheduler.internal;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.commandscheduler.CommandScheduler;
import nl.hauntedmc.serverfeatures.features.commandscheduler.config.CommandScheduleRepository;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.CommandSchedule;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ExecutionMode;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ScheduleTrigger;
import org.bukkit.scheduler.BukkitTask;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CommandSchedulerService {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    private final CommandScheduler feature;
    private final CommandScheduleRepository repository;
    private final ScheduledCommandExecutor executor;
    private final Clock clock;

    private Map<String, CommandSchedule> schedules = Map.of();
    private ZoneId zone;
    private BukkitTask pendingTask;
    private Instant pendingAt;
    private List<String> pendingScheduleIds = List.of();
    private long generation;

    public CommandSchedulerService(
            CommandScheduler feature,
            CommandScheduleRepository repository,
            ScheduledCommandExecutor executor,
            ZoneId zone
    ) {
        this(feature, repository, executor, zone, Clock.systemUTC());
    }

    CommandSchedulerService(
            CommandScheduler feature,
            CommandScheduleRepository repository,
            ScheduledCommandExecutor executor,
            ZoneId zone,
            Clock clock
    ) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized CommandScheduleRepository.LoadResult reload(ZoneId newZone) {
        repository.reload();
        CommandScheduleRepository.LoadResult result = repository.load();
        zone = Objects.requireNonNull(newZone, "newZone");
        schedules = immutableOrdered(result.schedules().values());
        rescheduleLocked();
        feature.getLogger().info(
                "Loaded " + schedules.size() + " command schedule(s) in zone " + zone
                        + (result.invalidCount() == 0
                        ? "."
                        : "; ignored " + result.invalidCount() + " invalid entry/entries.")
        );
        return result;
    }

    public synchronized List<CommandSchedule> list() {
        return List.copyOf(schedules.values());
    }

    public synchronized Optional<CommandSchedule> find(String rawId) {
        String id;
        try {
            id = CommandSchedule.normalizeId(rawId);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
        return Optional.ofNullable(schedules.get(id));
    }

    public synchronized MutationResult create(CommandSchedule schedule) {
        if (schedules.containsKey(schedule.id())) {
            return MutationResult.ALREADY_EXISTS;
        }
        if (schedule.enabled() && schedule.commands().isEmpty()) {
            return MutationResult.NO_COMMANDS;
        }
        LinkedHashMap<String, CommandSchedule> next = mutableSnapshot();
        next.put(schedule.id(), schedule);
        persistAndActivate(next);
        return MutationResult.SUCCESS;
    }

    public synchronized MutationResult delete(String rawId) {
        String id = normalizeExistingId(rawId);
        if (id == null) {
            return MutationResult.NOT_FOUND;
        }
        LinkedHashMap<String, CommandSchedule> next = mutableSnapshot();
        next.remove(id);
        persistAndActivate(next);
        return MutationResult.SUCCESS;
    }

    public synchronized MutationResult setEnabled(String rawId, boolean enabled) {
        CommandSchedule schedule = existing(rawId);
        if (schedule == null) {
            return MutationResult.NOT_FOUND;
        }
        if (enabled && schedule.commands().isEmpty()) {
            return MutationResult.NO_COMMANDS;
        }
        return replace(schedule.withEnabled(enabled));
    }

    public synchronized MutationResult setTrigger(String rawId, ScheduleTrigger trigger) {
        CommandSchedule schedule = existing(rawId);
        return schedule == null
                ? MutationResult.NOT_FOUND
                : replace(schedule.withTrigger(trigger));
    }

    public synchronized MutationResult setMode(String rawId, ExecutionMode mode) {
        CommandSchedule schedule = existing(rawId);
        return schedule == null
                ? MutationResult.NOT_FOUND
                : replace(schedule.withMode(mode));
    }

    public synchronized MutationResult addCommand(String rawId, String rawCommand) {
        CommandSchedule schedule = existing(rawId);
        if (schedule == null) {
            return MutationResult.NOT_FOUND;
        }
        List<String> commands = new ArrayList<>(schedule.commands());
        commands.add(CommandSchedule.normalizeCommand(rawCommand));
        return replace(schedule.withCommands(commands));
    }

    public synchronized MutationResult setCommand(String rawId, int oneBasedIndex, String rawCommand) {
        CommandSchedule schedule = existing(rawId);
        if (schedule == null) {
            return MutationResult.NOT_FOUND;
        }
        int index = oneBasedIndex - 1;
        if (index < 0 || index >= schedule.commands().size()) {
            return MutationResult.INVALID_INDEX;
        }
        List<String> commands = new ArrayList<>(schedule.commands());
        commands.set(index, CommandSchedule.normalizeCommand(rawCommand));
        return replace(schedule.withCommands(commands));
    }

    public synchronized MutationResult removeCommand(String rawId, int oneBasedIndex) {
        CommandSchedule schedule = existing(rawId);
        if (schedule == null) {
            return MutationResult.NOT_FOUND;
        }
        int index = oneBasedIndex - 1;
        if (index < 0 || index >= schedule.commands().size()) {
            return MutationResult.INVALID_INDEX;
        }
        if (schedule.enabled() && schedule.commands().size() == 1) {
            return MutationResult.WOULD_LEAVE_ENABLED_EMPTY;
        }
        List<String> commands = new ArrayList<>(schedule.commands());
        commands.remove(index);
        return replace(schedule.withCommands(commands));
    }

    public synchronized MutationResult runNow(String rawId, String actor) {
        CommandSchedule schedule = existing(rawId);
        if (schedule == null) {
            return MutationResult.NOT_FOUND;
        }
        if (schedule.commands().isEmpty()) {
            return MutationResult.NO_COMMANDS;
        }
        String source = actor == null || actor.isBlank()
                ? "manual"
                : "manual by " + actor;
        executor.enqueue(List.of(schedule), source);
        return MutationResult.SUCCESS;
    }

    public boolean isDispatchingScheduledCommand() {
        return executor.isDispatchingScheduledCommand();
    }

    public synchronized Optional<ZonedDateTime> nextRun(String rawId) {
        CommandSchedule schedule = existing(rawId);
        if (schedule == null || !schedule.enabled() || schedule.commands().isEmpty()) {
            return Optional.empty();
        }
        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.now(clock), zone);
        return Optional.of(nextOccurrence(schedule, now));
    }

    public synchronized Optional<ZonedDateTime> nextGlobalRun() {
        return pendingAt == null
                ? Optional.empty()
                : Optional.of(ZonedDateTime.ofInstant(pendingAt, zone));
    }

    public synchronized List<String> pendingScheduleIds() {
        return pendingScheduleIds;
    }

    public synchronized ZoneId zone() {
        return zone;
    }

    public String formatDateTime(ZonedDateTime dateTime) {
        return DATE_TIME_FORMAT.format(dateTime);
    }

    public synchronized void shutdown() {
        generation++;
        if (pendingTask != null) {
            feature.getLifecycleManager().getTaskManager().cancelTask(pendingTask);
            pendingTask = null;
        }
        pendingAt = null;
        pendingScheduleIds = List.of();
        executor.shutdown();
    }

    private MutationResult replace(CommandSchedule schedule) {
        LinkedHashMap<String, CommandSchedule> next = mutableSnapshot();
        next.put(schedule.id(), schedule);
        persistAndActivate(next);
        return MutationResult.SUCCESS;
    }

    private void persistAndActivate(Map<String, CommandSchedule> next) {
        Map<String, CommandSchedule> ordered = immutableOrdered(next.values());
        repository.save(ordered.values());
        schedules = ordered;
        rescheduleLocked();
    }

    private void rescheduleLocked() {
        generation++;
        long token = generation;
        if (pendingTask != null) {
            feature.getLifecycleManager().getTaskManager().cancelTask(pendingTask);
            pendingTask = null;
        }
        pendingAt = null;
        pendingScheduleIds = List.of();

        Instant earliest = null;
        List<String> dueIds = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.now(clock), zone);
        for (CommandSchedule schedule : schedules.values()) {
            if (!schedule.enabled() || schedule.commands().isEmpty()) {
                continue;
            }
            Instant occurrence = nextOccurrence(schedule, now).toInstant();
            if (earliest == null || occurrence.isBefore(earliest)) {
                earliest = occurrence;
                dueIds.clear();
                dueIds.add(schedule.id());
            } else if (occurrence.equals(earliest)) {
                dueIds.add(schedule.id());
            }
        }
        if (earliest == null) {
            return;
        }

        dueIds.sort(String::compareTo);
        pendingAt = earliest;
        pendingScheduleIds = List.copyOf(dueIds);
        scheduleCallback(token, earliest, pendingScheduleIds);
        feature.getLogger().info(
                "Next command schedule trigger: "
                        + formatDateTime(ZonedDateTime.ofInstant(earliest, zone))
                        + " (" + String.join(", ", pendingScheduleIds) + ")."
        );
    }

    private void scheduleCallback(long token, Instant triggerAt, List<String> scheduleIds) {
        long millis = Math.max(1L, Duration.between(Instant.now(clock), triggerAt).toMillis());
        long ticks = Math.max(1L, Math.ceilDiv(millis, 50L));
        pendingTask = feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                () -> handleTrigger(token, triggerAt, scheduleIds),
                BukkitTime.ticks(ticks)
        );
    }

    private void handleTrigger(long token, Instant triggerAt, List<String> scheduleIds) {
        List<CommandSchedule> due;
        synchronized (this) {
            if (token != generation) {
                return;
            }
            Instant now = Instant.now(clock);
            if (now.isBefore(triggerAt)) {
                scheduleCallback(token, triggerAt, scheduleIds);
                return;
            }

            pendingTask = null;
            due = scheduleIds.stream()
                    .map(schedules::get)
                    .filter(Objects::nonNull)
                    .filter(CommandSchedule::enabled)
                    .filter(schedule -> !schedule.commands().isEmpty())
                    .sorted(Comparator.comparing(CommandSchedule::id))
                    .toList();
            rescheduleLocked();
        }
        if (!due.isEmpty()) {
            executor.enqueue(due, "automatic");
        }
    }

    private ZonedDateTime nextOccurrence(
            CommandSchedule schedule,
            ZonedDateTime now
    ) {
        return ScheduleTimeCalculator.nextOccurrence(schedule.trigger(), now, zone);
    }

    private CommandSchedule existing(String rawId) {
        String id = normalizeExistingId(rawId);
        return id == null ? null : schedules.get(id);
    }

    private String normalizeExistingId(String rawId) {
        try {
            String id = CommandSchedule.normalizeId(rawId);
            return schedules.containsKey(id) ? id : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private LinkedHashMap<String, CommandSchedule> mutableSnapshot() {
        return new LinkedHashMap<>(schedules);
    }

    private static Map<String, CommandSchedule> immutableOrdered(
            Collection<CommandSchedule> values
    ) {
        LinkedHashMap<String, CommandSchedule> ordered = new LinkedHashMap<>();
        values.stream()
                .sorted(Comparator.comparing(CommandSchedule::id))
                .forEach(schedule -> ordered.put(schedule.id(), schedule));
        return Collections.unmodifiableMap(ordered);
    }

    public enum MutationResult {
        SUCCESS,
        NOT_FOUND,
        ALREADY_EXISTS,
        NO_COMMANDS,
        INVALID_INDEX,
        WOULD_LEAVE_ENABLED_EMPTY
    }
}
