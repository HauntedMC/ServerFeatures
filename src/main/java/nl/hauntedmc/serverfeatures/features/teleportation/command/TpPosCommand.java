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

public class TpPosCommand extends FeatureCommand {

    private static final String PERM = "serverfeatures.feature.teleportation.command.tppos";

    private final Teleportation feature;
    private final TeleportService service;

    public TpPosCommand(Teleportation feature, TeleportService service) {
        super("tppos");
        this.feature = feature;
        this.service = service;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player p)) {
            Msg.send(feature, sender, "teleportation.player_only", Map.of());
            return true;
        }
        if (!sender.hasPermission(PERM)) {
            Msg.send(feature, sender, "teleportation.no_permission", Map.of());
            return true;
        }
        if (args.length != 3) {
            Msg.send(feature, sender, "teleportation.usage.tppos", Map.of());
            return true;
        }

        Integer x = parseInt(args[0]);
        Integer y = parseInt(args[1]);
        Integer z = parseInt(args[2]);
        if (x == null || y == null || z == null) {
            Msg.send(feature, sender, "teleportation.coords.invalid", Map.of());
            return true;
        }

        service.tpPos(sender, p, x, y, z);
        return true;
    }

    private Integer parseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String[] args) {
        return List.of();
    }
}
