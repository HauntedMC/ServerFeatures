package nl.hauntedmc.serverfeatures.features.teleportation.command;

import nl.hauntedmc.serverfeatures.commands.FeatureCommand;
import nl.hauntedmc.serverfeatures.features.teleportation.Teleportation;
import nl.hauntedmc.serverfeatures.features.teleportation.service.TeleportService;
import nl.hauntedmc.serverfeatures.features.teleportation.util.Msg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class RandomTpCommand extends FeatureCommand {

    private static final String PERM = "serverfeatures.feature.teleportation.command.randomtp";
    private final Teleportation feature;
    private final TeleportService service;

    public RandomTpCommand(Teleportation feature, TeleportService service) {
        super("randomtp");
        this.feature = feature;
        this.service = service;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player p)) {
            Msg.send(feature, sender, "general.player_command", Map.of());
            return true;
        }
        if (!sender.hasPermission(PERM)) {
            Msg.send(feature, sender, "general.no_permission", Map.of());
            return true;
        }
        if (args.length != 0) {
            Msg.send(feature, sender, "teleportation.usage.randomtp", Map.of());
            return true;
        }

        service.randomTp(sender, p);
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String[] args) {
        return List.of();
    }
}
