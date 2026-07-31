package nl.hauntedmc.serverfeatures.features.restart.command;

import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.features.restart.Restart;
import nl.hauntedmc.serverfeatures.features.restart.internal.RestartService;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class RestartCommand extends FeatureCommand {

    private static final String BASE_PERMISSION =
            "serverfeatures.feature.restart.command.restart";
    private static final String FORCE_PERMISSION = BASE_PERMISSION + ".force";
    private static final String SCHEDULE_PERMISSION = BASE_PERMISSION + ".schedule";
    private static final String CANCEL_PERMISSION = BASE_PERMISSION + ".cancel";
    private static final String STATUS_PERMISSION = BASE_PERMISSION + ".status";

    private final Restart feature;
    private final RestartService service;

    public RestartCommand(Restart feature, RestartService service) {
        super(new CommandMeta.Builder("restart")
                .description("Manage a safe server restart lifecycle.")
                .usage("/restart [force|schedule <date|day> <time>|cancel|status]")
                .aliases(List.of("reboot"))
                .permission(BASE_PERMISSION)
                .build());
        this.feature = feature;
        this.service = service;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender,
                           @NotNull String label,
                           @NotNull String @NotNull [] args) {
        if (args.length == 0) {
            if (!sender.hasPermission(BASE_PERMISSION)) {
                sendNoPermission(sender);
                return true;
            }
            if (!service.startCommanded(sender)) {
                send(sender, "restart.in_progress");
                return true;
            }
            send(sender, "restart.started");
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "force" -> executeForce(sender);
            case "schedule" -> executeSchedule(sender, args);
            case "cancel" -> executeCancel(sender);
            case "status" -> executeStatus(sender);
            default -> send(sender, "restart.command.usage");
        }
        return true;
    }

    private void executeForce(CommandSender sender) {
        if (!canUse(sender, FORCE_PERMISSION)) {
            sendNoPermission(sender);
            return;
        }

        RestartService.Phase phase = service.getPhase();
        if (phase == RestartService.Phase.PREPARING
                || phase == RestartService.Phase.DRAINING
                || phase == RestartService.Phase.SHUTTING_DOWN) {
            send(sender, "restart.in_progress");
            return;
        }

        send(sender, "restart.forced");
        service.forceImmediate(sender);
    }

    private void executeSchedule(CommandSender sender, String[] args) {
        if (!canUse(sender, SCHEDULE_PERMISSION)) {
            sendNoPermission(sender);
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(service.getScheduleZone());
        ZonedDateTime target = RestartScheduleParser.parse(
                args,
                service.getScheduleZone(),
                now
        );
        if (target == null) {
            send(sender, "restart.schedule.invalid_datetime");
            send(sender, "restart.schedule.usage");
            return;
        }

        RestartService.ScheduleResult result = service.scheduleRestart(target);
        switch (result) {
            case SUCCESS -> sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("restart.schedule.set")
                    .with("datetime", service.formatDateTime(target))
                    .forAudience(sender)
                    .build());
            case ALREADY_ACTIVE -> send(sender, "restart.schedule.already_active");
            case NOT_IN_FUTURE -> send(sender, "restart.schedule.time_must_be_future");
        }
    }

    private void executeCancel(CommandSender sender) {
        if (!canUse(sender, CANCEL_PERMISSION)) {
            sendNoPermission(sender);
            return;
        }

        String messageKey = switch (service.cancelRestart()) {
            case NONE -> "restart.cancel.none";
            case SCHEDULED -> "restart.cancel.scheduled";
            case COUNTDOWN -> "restart.cancel.countdown";
            case FINAL_DELAY -> "restart.cancel.final_delay";
            case PREPARING -> "restart.cancel.preparing";
            case TOO_LATE -> "restart.cancel.too_late";
        };
        send(sender, messageKey);
    }

    private void executeStatus(CommandSender sender) {
        if (!canUse(sender, STATUS_PERMISSION)) {
            sendNoPermission(sender);
            return;
        }

        switch (service.getPhase()) {
            case IDLE -> send(sender, "restart.status.none");
            case SCHEDULED -> sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("restart.status.scheduled")
                    .with("datetime", service.formatDateTime(service.getScheduledAt()))
                    .forAudience(sender)
                    .build());
            case COUNTDOWN -> sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("restart.status.countdown")
                    .with("seconds", service.getRemainingSeconds())
                    .forAudience(sender)
                    .build());
            case FINAL_DELAY -> send(sender, "restart.status.final_delay");
            case PREPARING -> sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("restart.status.preparing")
                    .with("players", service.getPlayersRemaining())
                    .forAudience(sender)
                    .build());
            case DRAINING -> sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("restart.status.draining")
                    .with("players", service.getPlayersRemaining())
                    .forAudience(sender)
                    .build());
            case SHUTTING_DOWN -> send(sender, "restart.status.shutting_down");
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender,
                                             @NotNull String alias,
                                             @NotNull String @NotNull [] args) {
        if (args.length != 1) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Stream.of("force", "schedule", "cancel", "status")
                .filter(subcommand -> canUseSubcommand(sender, subcommand))
                .filter(subcommand -> subcommand.startsWith(prefix))
                .toList();
    }

    private boolean canUseSubcommand(CommandSender sender, String subcommand) {
        return switch (subcommand) {
            case "force" -> canUse(sender, FORCE_PERMISSION);
            case "schedule" -> canUse(sender, SCHEDULE_PERMISSION);
            case "cancel" -> canUse(sender, CANCEL_PERMISSION);
            case "status" -> canUse(sender, STATUS_PERMISSION);
            default -> false;
        };
    }

    private boolean canUse(CommandSender sender, String permission) {
        return sender.hasPermission(BASE_PERMISSION) || sender.hasPermission(permission);
    }

    private void sendNoPermission(CommandSender sender) {
        send(sender, "general.no_permission");
    }

    private void send(CommandSender sender, String key) {
        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage(key)
                .forAudience(sender)
                .build());
    }
}
