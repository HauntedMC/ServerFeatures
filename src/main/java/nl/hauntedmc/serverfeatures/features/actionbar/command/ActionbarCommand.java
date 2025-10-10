package nl.hauntedmc.serverfeatures.features.actionbar.command;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.api.command.tab.TabTree;
import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.Sources;
import nl.hauntedmc.serverfeatures.api.command.tab.types.ArgTypes;
import nl.hauntedmc.serverfeatures.features.actionbar.Actionbar;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ActionbarCommand extends FeatureCommand {

    private final Actionbar feature;

    public ActionbarCommand(Actionbar feature) {
        super(new CommandMeta.Builder("actionbar").build());
        this.feature = feature;
    }

    /** Provide the tab tree for the global TabCompleteListener/TabService. */
    @Override
    public TabTree createTabTree() {
        // Tooltip for seconds suggestions (arg-level tooltips via Source wrapper)
        var secondsWithTooltip = Sources.withTooltip(
                ArgTypes.integer(0, 3600).suggestions(),
                s -> Component.text("Duration in seconds (0 = once)")
        );

        return TabTree.builder()
                .root()
                .literal("start", cfg -> cfg
                        .require("serverfeatures.feature.actionbar.command.start")
                        .tooltip(Component.text("Begin the rotating actionbar messages")))
                .literal("stop",  cfg -> cfg
                        .require("serverfeatures.feature.actionbar.command.stop")
                        .tooltip(Component.text("Stop the rotating actionbar messages")))
                .literal("send",  cfg -> cfg
                        .require("serverfeatures.feature.actionbar.command.send")
                        .tooltip(Component.text("Send a one-off or timed actionbar message"))
                        .child()
                        .arg("message", ArgTypes.string()) // free text (no suggestions to hover)
                        .arg("seconds", ArgTypes.integer(0, 3600), secondsWithTooltip)
                )
                .build();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("serverfeatures.feature.actionbar.use")) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission").forAudience(sender).build());
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.usage").forAudience(sender).build());
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        switch (subCommand) {
            case "start":
                if (!sender.hasPermission("serverfeatures.feature.actionbar.command.start")) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission").forAudience(sender).build());
                    return true;
                }
                if (feature.getActionbarHandler().messageCycleRunning()) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.already_running").forAudience(sender).build());
                    return true;
                }
                feature.getActionbarHandler().startMessageCycle();
                sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.started").forAudience(sender).build());
                break;

            case "stop":
                if (!sender.hasPermission("serverfeatures.feature.actionbar.command.stop")) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission").forAudience(sender).build());
                    return true;
                }
                if (!feature.getActionbarHandler().messageCycleRunning()) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.not_running").forAudience(sender).build());
                    return true;
                }
                feature.getActionbarHandler().stopMessageCycle();
                sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.stopped").forAudience(sender).build());
                break;

            case "send":
                if (!sender.hasPermission("serverfeatures.feature.actionbar.command.send")) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission").forAudience(sender).build());
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.send_usage").forAudience(sender).build());
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
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.invalid_time").forAudience(sender).build());
                    return true;
                }
                feature.getActionbarHandler().sendManualActionbar(message, timeSeconds);

                Map<String, String> placeholders = new HashMap<>();
                if (timeSeconds > 0) {
                    placeholders.put("time", String.valueOf(timeSeconds));
                    placeholders.put("message", message);
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.sent_timer").forAudience(sender).withPlaceholders(placeholders).build());
                } else {
                    placeholders.put("message", message);
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.sent_once").forAudience(sender).withPlaceholders(placeholders).build());
                }
                break;

            default:
                sender.sendMessage(feature.getLocalizationHandler().getMessage("actionbar.usage").forAudience(sender).build());
                break;
        }

        return true;
    }
}
