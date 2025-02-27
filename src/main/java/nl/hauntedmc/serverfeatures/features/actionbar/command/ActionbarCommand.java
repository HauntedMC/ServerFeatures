package nl.hauntedmc.serverfeatures.features.actionbar.command;

import nl.hauntedmc.serverfeatures.commands.FeatureCommand;
import nl.hauntedmc.serverfeatures.features.actionbar.Actionbar;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ActionbarCommand extends FeatureCommand {
    private final Actionbar feature;

    public ActionbarCommand(Actionbar feature) {
        super("actionbar");
        this.feature = feature;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("serverfeatures.feature.actionbar.use")) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.usage"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "start":
                if (!sender.hasPermission("serverfeatures.feature.actionbar.command.start")) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission"));
                    return true;
                }
                if (feature.getActionbarHandler().messageCycleRunning()) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.already_running"));
                    return true;
                }
                feature.getActionbarHandler().startMessageCycle();
                sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.started"));
                break;

            case "stop":
                if (!sender.hasPermission("serverfeatures.feature.actionbar.command.stop")) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission"));
                    return true;
                }
                if (!feature.getActionbarHandler().messageCycleRunning()) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.not_running"));
                    return true;
                }
                feature.getActionbarHandler().stopMessageCycle();
                sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.stopped"));
                break;

            case "send":
                if (!sender.hasPermission("serverfeatures.feature.actionbar.command.send")) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.send_usage"));
                    return true;
                }

                String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                int timeSeconds = 0;
                String lastArg = args[args.length - 1];
                try {
                    timeSeconds = Integer.parseInt(lastArg);
                    message = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
                } catch (NumberFormatException ignored) {
                }
                if (timeSeconds < 0) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.invalid_time"));
                    return true;
                }
                feature.getActionbarHandler().sendManualActionbar(message, timeSeconds);

                Map<String, String> placeholders = new HashMap<>();
                if (timeSeconds > 0) {
                    placeholders.put("time", String.valueOf(timeSeconds));
                    placeholders.put("message", message);
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.sent_timer", placeholders));
                } else {
                    placeholders.put("message", message);
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.sent_once", placeholders));
                }
                break;

            default:
                sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.usage"));
                break;
        }

        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender,
                                             @NotNull String alias,
                                             String @NotNull [] args) {
        // /actionbar <subcommand>
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            for (String option : Arrays.asList("start", "stop", "send")) {
                if (option.startsWith(input)) {
                    suggestions.add(option);
                }
            }
            return suggestions;
        }
        return Collections.emptyList();
    }
}
