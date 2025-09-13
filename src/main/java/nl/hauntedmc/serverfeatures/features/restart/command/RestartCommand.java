package nl.hauntedmc.serverfeatures.features.restart.command;

import nl.hauntedmc.serverfeatures.commands.CommandSpec;
import nl.hauntedmc.serverfeatures.commands.FeatureCommand;
import nl.hauntedmc.serverfeatures.features.restart.Restart;
import nl.hauntedmc.serverfeatures.features.restart.internal.RestartService;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RestartCommand extends FeatureCommand {

    // Base and force permissions
    private static final String PERM = "serverfeatures.feature.restart.command.restart";
    private static final String PERM_FORCE = "serverfeatures.feature.restart.command.restart.force";

    private final Restart feature;
    private final RestartService service;

    public RestartCommand(Restart feature, RestartService service) {
        super(new CommandSpec.Builder("restart")
                .description("Restart the server with a countdown, or immediately with 'force'.")
                .usage("/restart [force]")
                .aliases(List.of("reboot"))
                .permission(PERM)
                .build());
        this.feature = feature;
        this.service = service;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender,
                           @NotNull String label,
                           @NotNull String @NotNull [] args) {

        // Handle forced restart: skip sequencing, save-all and shutdown immediately
        if (args.length >= 1 && args[0].equalsIgnoreCase("force")) {
            if (!(sender.hasPermission(PERM_FORCE) || sender.hasPermission(PERM))) {
                sender.sendMessage(feature.getLocalizationHandler()
                        .getMessage("general.no_permission")
                        .forAudience(sender)
                        .build());
                return true;
            }
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("restart.forced")
                    .forAudience(sender)
                    .build());

            service.forceImmediate(sender);

            return true;
        }

        // Normal countdown-based restart
        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("general.no_permission")
                    .forAudience(sender)
                    .build());
            return true;
        }

        if (!service.startCommanded(sender)) {
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("restart.in_progress")
                    .forAudience(sender)
                    .build());
            return true;
        }

        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage("restart.started")
                .forAudience(sender)
                .build());
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender,
                                             @NotNull String alias,
                                             @NotNull String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            boolean canForce = sender.hasPermission(PERM_FORCE) || sender.hasPermission(PERM);
            if (canForce && "force".startsWith(p)) return List.of("force");
        }
        return List.of();
    }
}
