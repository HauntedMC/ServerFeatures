package nl.hauntedmc.serverfeatures.features.teleportation.command;

import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.features.teleportation.Teleportation;
import nl.hauntedmc.serverfeatures.features.teleportation.service.TeleportService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TpPosCommand extends FeatureCommand {

    private static final String PERM = "serverfeatures.feature.teleportation.command.tppos";
    private final Teleportation feature;
    private final TeleportService service;

    public TpPosCommand(Teleportation feature, TeleportService service) {
        super(new CommandMeta.Builder("tppos").build());
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
        if (args.length != 3) {
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("teleportation.usage.tppos")
                    .forAudience(sender)
                    .build());
            return true;
        }

        Integer x = parseInt(args[0]);
        Integer y = parseInt(args[1]);
        Integer z = parseInt(args[2]);
        if (x == null || y == null || z == null) {
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("teleportation.tppos.coords_invalid")
                    .forAudience(sender)
                    .build());
            return true;
        }

        service.tpPos(sender, p, x, y, z);
        return true;
    }

    private Integer parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull [] args) {
        return List.of();
    }
}
