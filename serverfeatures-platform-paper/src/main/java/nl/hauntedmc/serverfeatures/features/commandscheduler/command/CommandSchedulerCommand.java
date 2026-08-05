package nl.hauntedmc.serverfeatures.features.commandscheduler.command;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.features.commandscheduler.CommandScheduler;
import nl.hauntedmc.serverfeatures.features.commandscheduler.config.CommandScheduleRepository;
import nl.hauntedmc.serverfeatures.features.commandscheduler.internal.CommandSchedulerService;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.CommandSchedule;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ExecutionMode;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ScheduleParser;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ScheduleTrigger;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ScheduleType;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public final class CommandSchedulerCommand extends FeatureCommand {

    private static final int PAGE_SIZE = 8;
    private static final String BASE_PERMISSION =
            "serverfeatures.feature.commandscheduler.command.commandscheduler";
    private static final String VIEW_PERMISSION = BASE_PERMISSION + ".view";
    private static final String MANAGE_PERMISSION = BASE_PERMISSION + ".manage";
    private static final String RUN_PERMISSION = BASE_PERMISSION + ".run";
    private static final String RELOAD_PERMISSION = BASE_PERMISSION + ".reload";

    private final CommandScheduler feature;
    private final CommandSchedulerService service;

    public CommandSchedulerCommand(
            CommandScheduler feature,
            CommandSchedulerService service
    ) {
        super(new CommandMeta.Builder("commandscheduler")
                .description("Manage recurring console command schedules.")
                .usage("/commandscheduler <subcommand>")
                .aliases(List.of("cmdscheduler"))
                .permission(BASE_PERMISSION)
                .build());
        this.feature = feature;
        this.service = service;
    }

    @Override
    public boolean execute(
            @NotNull CommandSender sender,
            @NotNull String label,
            @NotNull String @NotNull [] args
    ) {
        if (args.length == 0) {
            send(sender, "commandscheduler.usage");
            return true;
        }

        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "list" -> executeList(sender, args);
                case "info" -> executeInfo(sender, args);
                case "create" -> executeCreate(sender, args);
                case "delete" -> executeDelete(sender, args);
                case "enable" -> executeEnable(sender, args, true);
                case "disable" -> executeEnable(sender, args, false);
                case "set" -> executeSet(sender, args);
                case "command" -> executeCommand(sender, args);
                case "run" -> executeRun(sender, args);
                case "reload" -> executeReload(sender, args);
                default -> send(sender, "commandscheduler.usage");
            }
        } catch (IllegalStateException exception) {
            feature.getLogger().severe(
                    "CommandScheduler persistence operation failed: " + rootMessage(exception)
            );
            send(sender, "commandscheduler.persistence_error");
        }
        return true;
    }

    private void executeList(CommandSender sender, String[] args) {
        if (!canView(sender)) {
            sendNoPermission(sender);
            return;
        }
        if (args.length > 2) {
            send(sender, "commandscheduler.usage");
            return;
        }

        List<CommandSchedule> schedules = service.list();
        if (schedules.isEmpty()) {
            send(sender, "commandscheduler.list_empty");
            return;
        }
        int pages = Math.max(1, Math.ceilDiv(schedules.size(), PAGE_SIZE));
        int page = 1;
        if (args.length == 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                sendInvalid(sender, "pagina moet een getal zijn");
                return;
            }
        }
        if (page < 1 || page > pages) {
            sendInvalid(sender, "pagina moet tussen 1 en " + pages + " liggen");
            return;
        }

        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage("commandscheduler.list_header")
                .with("page", page)
                .with("pages", pages)
                .with("zone", service.zone().getId())
                .forAudience(sender)
                .build());

        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(schedules.size(), from + PAGE_SIZE);
        for (CommandSchedule schedule : schedules.subList(from, to)) {
            String next = service.nextRun(schedule.id())
                    .map(service::formatDateTime)
                    .orElse("-");
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("commandscheduler.list_entry")
                    .with("id", schedule.id())
                    .with("state", schedule.enabled() ? "&aaan" : "&cuit")
                    .with("trigger", formatTrigger(schedule.trigger()))
                    .with("mode", formatMode(schedule.mode()))
                    .with("commands", schedule.commands().size())
                    .with("next", next)
                    .forAudience(sender)
                    .build());
        }
    }

    private void executeInfo(CommandSender sender, String[] args) {
        if (!canView(sender)) {
            sendNoPermission(sender);
            return;
        }
        if (args.length != 2) {
            send(sender, "commandscheduler.usage");
            return;
        }
        Optional<CommandSchedule> optional = service.find(args[1]);
        if (optional.isEmpty()) {
            sendNotFound(sender, args[1]);
            return;
        }
        CommandSchedule schedule = optional.get();
        String next = service.nextRun(schedule.id())
                .map(service::formatDateTime)
                .orElse("-");

        sendWithId(sender, "commandscheduler.info_header", schedule.id());
        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage("commandscheduler.info_state")
                .with("state", schedule.enabled() ? "&aingeschakeld" : "&cuitgeschakeld")
                .forAudience(sender)
                .build());
        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage("commandscheduler.info_trigger")
                .with("trigger", formatTrigger(schedule.trigger()))
                .forAudience(sender)
                .build());
        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage("commandscheduler.info_mode")
                .with("mode", formatMode(schedule.mode()))
                .forAudience(sender)
                .build());
        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage("commandscheduler.info_commands")
                .with("commands", schedule.commands().size())
                .forAudience(sender)
                .build());
        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage("commandscheduler.info_next")
                .with("next", next)
                .forAudience(sender)
                .build());
    }

    private void executeCreate(CommandSender sender, String[] args) {
        if (!canUse(sender, MANAGE_PERMISSION)) {
            sendNoPermission(sender);
            return;
        }
        try {
            ParsedTrigger parsed = parseCreateTrigger(args);
            CommandSchedule schedule = new CommandSchedule(
                    args[1],
                    false,
                    parsed.trigger(),
                    parsed.mode(),
                    List.of()
            );
            CommandSchedulerService.MutationResult result = service.create(schedule);
            if (result == CommandSchedulerService.MutationResult.ALREADY_EXISTS) {
                sendWithId(sender, "commandscheduler.already_exists", schedule.id());
                return;
            }
            sendWithId(sender, "commandscheduler.created", schedule.id());
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (args.length < 2) {
                send(sender, "commandscheduler.create_usage");
            } else {
                sendInvalid(sender, rootMessage(exception));
                send(sender, "commandscheduler.create_usage");
            }
        }
    }

    private void executeDelete(CommandSender sender, String[] args) {
        if (!canUse(sender, MANAGE_PERMISSION)) {
            sendNoPermission(sender);
            return;
        }
        if (args.length != 2) {
            send(sender, "commandscheduler.usage");
            return;
        }
        CommandSchedulerService.MutationResult result = service.delete(args[1]);
        if (result == CommandSchedulerService.MutationResult.NOT_FOUND) {
            sendNotFound(sender, args[1]);
            return;
        }
        sendWithId(sender, "commandscheduler.deleted", args[1]);
    }

    private void executeEnable(CommandSender sender, String[] args, boolean enabled) {
        if (!canUse(sender, MANAGE_PERMISSION)) {
            sendNoPermission(sender);
            return;
        }
        if (args.length != 2) {
            send(sender, "commandscheduler.usage");
            return;
        }
        CommandSchedulerService.MutationResult result = service.setEnabled(args[1], enabled);
        switch (result) {
            case SUCCESS -> sendWithId(
                    sender,
                    enabled ? "commandscheduler.enabled" : "commandscheduler.disabled",
                    args[1]
            );
            case NOT_FOUND -> sendNotFound(sender, args[1]);
            case NO_COMMANDS -> sendWithId(sender, "commandscheduler.enabled_empty", args[1]);
            default -> sendInvalid(sender, result.name().toLowerCase(Locale.ROOT));
        }
    }

    private void executeSet(CommandSender sender, String[] args) {
        if (!canUse(sender, MANAGE_PERMISSION)) {
            sendNoPermission(sender);
            return;
        }
        if (args.length < 2) {
            send(sender, "commandscheduler.set_usage");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "schedule" -> executeSetSchedule(sender, args);
            case "mode" -> executeSetMode(sender, args);
            default -> send(sender, "commandscheduler.set_usage");
        }
    }

    private void executeSetSchedule(CommandSender sender, String[] args) {
        try {
            ScheduleTrigger trigger = parseSetTrigger(args);
            CommandSchedulerService.MutationResult result = service.setTrigger(args[2], trigger);
            if (result == CommandSchedulerService.MutationResult.NOT_FOUND) {
                sendNotFound(sender, args[2]);
                return;
            }
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("commandscheduler.schedule_set")
                    .with("id", args[2])
                    .with("trigger", formatTrigger(trigger))
                    .forAudience(sender)
                    .build());
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            sendInvalid(sender, rootMessage(exception));
            send(sender, "commandscheduler.set_usage");
        }
    }

    private void executeSetMode(CommandSender sender, String[] args) {
        if (args.length != 4) {
            send(sender, "commandscheduler.set_usage");
            return;
        }
        try {
            ExecutionMode mode = ScheduleParser.parseMode(args[3]);
            CommandSchedulerService.MutationResult result = service.setMode(args[2], mode);
            if (result == CommandSchedulerService.MutationResult.NOT_FOUND) {
                sendNotFound(sender, args[2]);
                return;
            }
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("commandscheduler.mode_set")
                    .with("id", args[2])
                    .with("mode", formatMode(mode))
                    .forAudience(sender)
                    .build());
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            sendInvalid(sender, rootMessage(exception));
        }
    }

    private void executeCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "commandscheduler.command_usage");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            executeCommandList(sender, args);
            return;
        }
        if (!canUse(sender, MANAGE_PERMISSION)) {
            sendNoPermission(sender);
            return;
        }
        switch (action) {
            case "add" -> executeCommandAdd(sender, args);
            case "set" -> executeCommandSet(sender, args);
            case "remove" -> executeCommandRemove(sender, args);
            default -> send(sender, "commandscheduler.command_usage");
        }
    }

    private void executeCommandList(CommandSender sender, String[] args) {
        if (!canView(sender)) {
            sendNoPermission(sender);
            return;
        }
        if (args.length != 3) {
            send(sender, "commandscheduler.command_usage");
            return;
        }
        Optional<CommandSchedule> optional = service.find(args[2]);
        if (optional.isEmpty()) {
            sendNotFound(sender, args[2]);
            return;
        }
        CommandSchedule schedule = optional.get();
        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage("commandscheduler.command_list_header")
                .with("id", schedule.id())
                .with("mode", formatMode(schedule.mode()))
                .forAudience(sender)
                .build());
        if (schedule.commands().isEmpty()) {
            send(sender, "commandscheduler.command_list_empty");
            return;
        }
        for (int index = 0; index < schedule.commands().size(); index++) {
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("commandscheduler.command_list_entry")
                    .with("index", index + 1)
                    .with("command", Component.text(schedule.commands().get(index)))
                    .forAudience(sender)
                    .build());
        }
    }

    private void executeCommandAdd(CommandSender sender, String[] args) {
        if (args.length < 4) {
            send(sender, "commandscheduler.command_usage");
            return;
        }
        try {
            String command = joinTail(args, 3);
            CommandSchedulerService.MutationResult result = service.addCommand(args[2], command);
            if (result == CommandSchedulerService.MutationResult.NOT_FOUND) {
                sendNotFound(sender, args[2]);
                return;
            }
            int index = service.find(args[2]).orElseThrow().commands().size();
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("commandscheduler.command_added")
                    .with("id", args[2])
                    .with("index", index)
                    .forAudience(sender)
                    .build());
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            sendInvalid(sender, rootMessage(exception));
        }
    }

    private void executeCommandSet(CommandSender sender, String[] args) {
        if (args.length < 5) {
            send(sender, "commandscheduler.command_usage");
            return;
        }
        Integer index = parseIndex(sender, args[3]);
        if (index == null) {
            return;
        }
        try {
            CommandSchedulerService.MutationResult result = service.setCommand(
                    args[2],
                    index,
                    joinTail(args, 4)
            );
            handleCommandMutationResult(sender, args[2], index, result, "commandscheduler.command_set");
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            sendInvalid(sender, rootMessage(exception));
        }
    }

    private void executeCommandRemove(CommandSender sender, String[] args) {
        if (args.length != 4) {
            send(sender, "commandscheduler.command_usage");
            return;
        }
        Integer index = parseIndex(sender, args[3]);
        if (index == null) {
            return;
        }
        CommandSchedulerService.MutationResult result = service.removeCommand(args[2], index);
        handleCommandMutationResult(
                sender,
                args[2],
                index,
                result,
                "commandscheduler.command_removed"
        );
    }

    private void handleCommandMutationResult(
            CommandSender sender,
            String id,
            int index,
            CommandSchedulerService.MutationResult result,
            String successKey
    ) {
        switch (result) {
            case SUCCESS -> sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage(successKey)
                    .with("id", id)
                    .with("index", index)
                    .forAudience(sender)
                    .build());
            case NOT_FOUND -> sendNotFound(sender, id);
            case INVALID_INDEX -> sendWithId(sender, "commandscheduler.invalid_index", id);
            case WOULD_LEAVE_ENABLED_EMPTY -> sendWithId(sender, "commandscheduler.last_command", id);
            default -> sendInvalid(sender, result.name().toLowerCase(Locale.ROOT));
        }
    }

    private void executeRun(CommandSender sender, String[] args) {
        if (!canUse(sender, RUN_PERMISSION)) {
            sendNoPermission(sender);
            return;
        }
        if (args.length != 2) {
            send(sender, "commandscheduler.usage");
            return;
        }
        if (service.isDispatchingScheduledCommand()) {
            send(sender, "commandscheduler.recursive_run");
            return;
        }
        CommandSchedulerService.MutationResult result = service.runNow(
                args[1],
                sender.getName()
        );
        switch (result) {
            case SUCCESS -> sendWithId(sender, "commandscheduler.run_started", args[1]);
            case NOT_FOUND -> sendNotFound(sender, args[1]);
            case NO_COMMANDS -> sendWithId(sender, "commandscheduler.no_commands", args[1]);
            default -> sendInvalid(sender, result.name().toLowerCase(Locale.ROOT));
        }
    }

    private void executeReload(CommandSender sender, String[] args) {
        if (!canUse(sender, RELOAD_PERMISSION)) {
            sendNoPermission(sender);
            return;
        }
        if (args.length != 1) {
            send(sender, "commandscheduler.usage");
            return;
        }
        CommandScheduleRepository.LoadResult result = feature.reloadSchedules();
        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage("commandscheduler.reloaded")
                .with("loaded", result.schedules().size())
                .with("invalid", result.invalidCount())
                .with("zone", service.zone().getId())
                .forAudience(sender)
                .build());
    }

    private ParsedTrigger parseCreateTrigger(String[] args) {
        if (args.length < 5) {
            throw new IllegalArgumentException("onvoldoende argumenten");
        }
        ScheduleType type = ScheduleParser.parseType(args[2]);
        if (type == ScheduleType.DAILY) {
            if (args.length != 5) {
                throw new IllegalArgumentException("daily verwacht <HH:mm> <mode>");
            }
            return new ParsedTrigger(
                    ScheduleTrigger.daily(ScheduleParser.parseTime(args[3])),
                    ScheduleParser.parseMode(args[4])
            );
        }
        if (args.length != 6) {
            throw new IllegalArgumentException("weekly verwacht <dag> <HH:mm> <mode>");
        }
        return new ParsedTrigger(
                ScheduleTrigger.weekly(
                        ScheduleParser.parseDay(args[3]),
                        ScheduleParser.parseTime(args[4])
                ),
                ScheduleParser.parseMode(args[5])
        );
    }

    private ScheduleTrigger parseSetTrigger(String[] args) {
        if (args.length < 5) {
            throw new IllegalArgumentException("onvoldoende argumenten");
        }
        ScheduleType type = ScheduleParser.parseType(args[3]);
        if (type == ScheduleType.DAILY) {
            if (args.length != 5) {
                throw new IllegalArgumentException("daily verwacht <HH:mm>");
            }
            return ScheduleTrigger.daily(ScheduleParser.parseTime(args[4]));
        }
        if (args.length != 6) {
            throw new IllegalArgumentException("weekly verwacht <dag> <HH:mm>");
        }
        return ScheduleTrigger.weekly(
                ScheduleParser.parseDay(args[4]),
                ScheduleParser.parseTime(args[5])
        );
    }

    private Integer parseIndex(CommandSender sender, String raw) {
        try {
            int index = Integer.parseInt(raw);
            if (index < 1) {
                throw new NumberFormatException("index below one");
            }
            return index;
        } catch (NumberFormatException ignored) {
            sendInvalid(sender, "index moet een positief getal zijn");
            return null;
        }
    }

    private String joinTail(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    private String formatTrigger(ScheduleTrigger trigger) {
        if (trigger.type() == ScheduleType.DAILY) {
            return "daily " + trigger.time();
        }
        return trigger.day().name().toLowerCase(Locale.ROOT) + " " + trigger.time();
    }

    private String formatMode(ExecutionMode mode) {
        return mode.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public @NotNull List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String alias,
            @NotNull String @NotNull [] args
    ) {
        if (args.length == 0) {
            return List.of();
        }
        if (args.length == 1) {
            return filterPrefix(rootSubcommands(sender), args[0]);
        }

        String root = args[0].toLowerCase(Locale.ROOT);
        return switch (root) {
            case "info" -> args.length == 2 && canView(sender)
                    ? scheduleIds(args[1]) : List.of();
            case "delete", "enable", "disable" ->
                    args.length == 2 && canUse(sender, MANAGE_PERMISSION)
                            ? scheduleIds(args[1]) : List.of();
            case "run" -> args.length == 2 && canUse(sender, RUN_PERMISSION)
                    ? scheduleIds(args[1]) : List.of();
            case "create" -> canUse(sender, MANAGE_PERMISSION)
                    ? completeCreate(args) : List.of();
            case "set" -> canUse(sender, MANAGE_PERMISSION)
                    ? completeSet(args) : List.of();
            case "command" -> canView(sender)
                    ? completeCommand(args) : List.of();
            default -> List.of();
        };
    }

    private List<String> rootSubcommands(CommandSender sender) {
        List<String> out = new ArrayList<>();
        if (canView(sender)) {
            out.add("list");
            out.add("info");
        }
        if (canUse(sender, MANAGE_PERMISSION)) {
            out.addAll(List.of("create", "delete", "enable", "disable", "set", "command"));
        } else if (canView(sender)) {
            out.add("command");
        }
        if (canUse(sender, RUN_PERMISSION)) {
            out.add("run");
        }
        if (canUse(sender, RELOAD_PERMISSION)) {
            out.add("reload");
        }
        return out;
    }

    private List<String> completeCreate(String[] args) {
        if (args.length == 3) {
            return filterPrefix(List.of("daily", "weekly"), args[2]);
        }
        if (args.length == 4 && args[2].equalsIgnoreCase("weekly")) {
            return daySuggestions(args[3]);
        }
        if (args.length == 4 && args[2].equalsIgnoreCase("daily")) {
            return timeSuggestions(args[3]);
        }
        if (args.length == 5 && args[2].equalsIgnoreCase("weekly")) {
            return timeSuggestions(args[4]);
        }
        if ((args.length == 5 && args[2].equalsIgnoreCase("daily"))
                || (args.length == 6 && args[2].equalsIgnoreCase("weekly"))) {
            return filterPrefix(List.of("sequence", "random"), args[args.length - 1]);
        }
        return List.of();
    }

    private List<String> completeSet(String[] args) {
        if (args.length == 2) {
            return filterPrefix(List.of("schedule", "mode"), args[1]);
        }
        if (args.length == 3) {
            return scheduleIds(args[2]);
        }
        if (args[1].equalsIgnoreCase("mode") && args.length == 4) {
            return filterPrefix(List.of("sequence", "random"), args[3]);
        }
        if (!args[1].equalsIgnoreCase("schedule")) {
            return List.of();
        }
        if (args.length == 4) {
            return filterPrefix(List.of("daily", "weekly"), args[3]);
        }
        if (args.length == 5 && args[3].equalsIgnoreCase("weekly")) {
            return daySuggestions(args[4]);
        }
        if (args.length == 5 && args[3].equalsIgnoreCase("daily")) {
            return timeSuggestions(args[4]);
        }
        if (args.length == 6 && args[3].equalsIgnoreCase("weekly")) {
            return timeSuggestions(args[5]);
        }
        return List.of();
    }

    private List<String> completeCommand(String[] args) {
        if (args.length == 2) {
            return filterPrefix(List.of("list", "add", "set", "remove"), args[1]);
        }
        if (args.length == 3) {
            return scheduleIds(args[2]);
        }
        if (args.length == 4
                && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("remove"))) {
            Optional<CommandSchedule> schedule = service.find(args[2]);
            if (schedule.isEmpty()) {
                return List.of();
            }
            List<String> indices = Stream.iterate(1, index -> index + 1)
                    .limit(schedule.get().commands().size())
                    .map(String::valueOf)
                    .toList();
            return filterPrefix(indices, args[3]);
        }
        return List.of();
    }

    private List<String> scheduleIds(String prefix) {
        return filterPrefix(service.list().stream().map(CommandSchedule::id).toList(), prefix);
    }

    private List<String> daySuggestions(String prefix) {
        return filterPrefix(Arrays.stream(DayOfWeek.values())
                .map(day -> day.name().toLowerCase(Locale.ROOT))
                .toList(), prefix);
    }

    private List<String> timeSuggestions(String prefix) {
        return filterPrefix(List.of("04:00", "12:00", "20:00", "21:00"), prefix);
    }

    private List<String> filterPrefix(List<String> values, String rawPrefix) {
        String prefix = rawPrefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }

    private boolean canView(CommandSender sender) {
        return canUse(sender, VIEW_PERMISSION) || canUse(sender, MANAGE_PERMISSION);
    }

    private boolean canUse(CommandSender sender, String permission) {
        return sender.hasPermission(BASE_PERMISSION) || sender.hasPermission(permission);
    }

    private void sendNoPermission(CommandSender sender) {
        send(sender, "general.no_permission");
    }

    private void sendNotFound(CommandSender sender, String id) {
        sendWithId(sender, "commandscheduler.not_found", id);
    }

    private void sendWithId(CommandSender sender, String key, String id) {
        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage(key)
                .with("id", Component.text(id))
                .forAudience(sender)
                .build());
    }

    private void sendInvalid(CommandSender sender, String reason) {
        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage("commandscheduler.invalid_input")
                .with("reason", Component.text(reason))
                .forAudience(sender)
                .build());
    }

    private void send(CommandSender sender, String key) {
        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage(key)
                .forAudience(sender)
                .build());
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

    private record ParsedTrigger(ScheduleTrigger trigger, ExecutionMode mode) {
    }
}
