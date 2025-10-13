package nl.hauntedmc.serverfeatures.features.teleportation.command;

import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.features.teleportation.Teleportation;
import nl.hauntedmc.serverfeatures.features.teleportation.service.TeleportService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RandomTpCommand extends FeatureCommand {

    private static final String PERM = "serverfeatures.feature.teleportation.command.randomtp";
    private final Teleportation feature;
    private final TeleportService service;

    public RandomTpCommand(Teleportation feature, TeleportService service) {
        super(new CommandMeta.Builder("randomtp").aliases(List.of("rtp")).build());
        this.feature = feature;
        this.service = service;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("general.player_command")
                    .forAudience(sender)
                    .build());
            return true;
        }
        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("general.no_permission")
                    .forAudience(sender)
                    .build());
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("teleportation.usage.randomtp")
                    .forAudience(sender)
                    .build());
            return true;
        }

        service.randomTp(sender, p);
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull [] args) {
        return List.of();
    }
}
