package nl.hauntedmc.serverfeatures.features.commandrelay.command;

import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.features.commandrelay.CommandRelay;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class CommandRelayCommand extends FeatureCommand {
    private final CommandRelay feature;

    public CommandRelayCommand(CommandRelay feature) {
        super(new CommandMeta.Builder("commandrelay").build());
        this.feature = feature;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender,
                           @NotNull String label,
                           String @NotNull [] args) {

        if (!sender.hasPermission("serverfeatures.feature.commandrelay.command.use")) {
            sender.sendMessage(
                    feature.getLocalizationHandler().getMessage("general.no_permission")
                            .forAudience(sender)
                            .build()
            );
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(
                    feature.getLocalizationHandler().getMessage("commandrelay.usage")
                            .forAudience(sender)
                            .build()
            );
            return true;
        }

        String target = args[0];
        String cmd = String.join(" ",
                Arrays.copyOfRange(args, 1, args.length)
        );

        String channel = target + ".commandrelay.command";
        feature.getEventBusHandler().publish(channel, cmd);

        sender.sendMessage(
                feature.getLocalizationHandler().getMessage("commandrelay.relayed")
                        .with("target", target)
                        .with("cmd", cmd)
                        .forAudience(sender)
                        .build()
        );
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender,
                                             @NotNull String alias,
                                             String @NotNull [] args) {
        return List.of();
    }
}
