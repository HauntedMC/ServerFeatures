package nl.hauntedmc.serverfeatures.features.restart.command;

import nl.hauntedmc.serverfeatures.commands.CommandSpec;
import nl.hauntedmc.serverfeatures.commands.FeatureCommand;
import nl.hauntedmc.serverfeatures.features.restart.Restart;
import nl.hauntedmc.serverfeatures.features.restart.internal.RestartService;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class RestartCommand extends FeatureCommand {

    private final Restart feature;
    private final RestartService service;

    public RestartCommand(Restart feature, RestartService service) {
        super(new CommandSpec.Builder("restart").build());
        this.feature = feature;
        this.service = service;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender,
                           @NotNull String label,
                           @NotNull String[] args) {

        if (!sender.hasPermission("serverfeatures.feature.restart.command.restart")) {
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
        return Collections.emptyList();
    }
}
