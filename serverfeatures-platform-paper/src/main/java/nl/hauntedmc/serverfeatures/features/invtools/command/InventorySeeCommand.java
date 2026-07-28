package nl.hauntedmc.serverfeatures.features.invtools.command;

import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.features.invtools.InvTools;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.service.InvToolsService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public final class InventorySeeCommand extends FeatureCommand {

    private final InvTools feature;
    private final InventoryKind kind;

    public InventorySeeCommand(InvTools feature, InventoryKind kind) {
        super(new CommandMeta.Builder(kind.commandName())
                .description(kind == InventoryKind.PLAYER
                        ? "Inspect a player's inventory"
                        : "Inspect a player's ender chest")
                .usage("/" + kind.commandName() + " <name>")
                .build());
        this.feature = feature;
        this.kind = kind;
    }

    @Override
    public boolean execute(
            @NotNull CommandSender sender,
            @NotNull String label,
            String @NotNull [] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("general.player_command")
                    .forAudience(sender)
                    .build());
            return true;
        }
        if (!player.hasPermission(InvToolsService.inspectPermission(kind))) {
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("general.no_permission")
                    .forAudience(sender)
                    .build());
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("invtools.usage." + kind.commandName())
                    .forAudience(sender)
                    .build());
            return true;
        }

        feature.getService().open(player, args[0], kind);
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String alias,
            String @NotNull [] args
    ) {
        if (args.length != 1 || !sender.hasPermission(InvToolsService.inspectPermission(kind))) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(20)
                .toList();
    }
}
