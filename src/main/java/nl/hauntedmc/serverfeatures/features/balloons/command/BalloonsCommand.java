package nl.hauntedmc.serverfeatures.features.balloons.command;

import nl.hauntedmc.serverfeatures.commands.CommandSpec;
import nl.hauntedmc.serverfeatures.commands.FeatureCommand;
import nl.hauntedmc.serverfeatures.features.balloons.Balloons;
import nl.hauntedmc.serverfeatures.features.balloons.menu.BalloonsMenu;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * /balloon
 * - opens the balloons menu
 * /balloon remove
 * - removes the current balloon
 */
public class BalloonsCommand extends FeatureCommand {

    private final Balloons feature;

    public BalloonsCommand(Balloons feature) {
        super(new CommandSpec.Builder("balloon").build());
        this.feature = feature;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(sender).build());
            return true;
        }

        if (player.isInsideVehicle()) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("balloons.cannot_open_vehicle").forAudience(player).build());
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("remove")) {
            boolean removed = feature.getHandler().removeBalloon(player);
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage(removed ? "balloons.removed" : "balloons.no_active")
                    .forAudience(player).build());
            return true;
        }

        if (!player.hasPermission("serverfeatures.feature.balloons.use")) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("general.no_permission").forAudience(player).build());
            return true;
        }

        BalloonsMenu.open(feature, player);
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) {
            return Stream.of("remove")
                    .filter(opt -> opt.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
