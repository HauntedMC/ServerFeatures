package nl.hauntedmc.serverfeatures.features.glow.command;

import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.features.glow.Glow;
import nl.hauntedmc.serverfeatures.features.glow.menu.GlowMenu;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Command executor for the /glow command.
 * Behavior:
 * - /glow           => opens the glow selection menu
 * - /glow remove    => removes current glow (notifies if none was active)
 */
public class GlowCommand extends FeatureCommand {

    private final Glow feature;

    public GlowCommand(Glow feature) {
        super(new CommandMeta.Builder("glow").build());
        this.feature = feature;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(sender).build());
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("remove")) {
            boolean had = feature.getGlowHandler().hasActiveGlow(player);
            boolean removed = feature.getGlowHandler().removeGlow(player);
            if (removed) {
                if (had) {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("glow.glow_removed").forAudience(sender).build());
                } else {
                    sender.sendMessage(feature.getLocalizationHandler().getMessage("glow.no_active_glow").forAudience(sender).build());
                }
            }
            return true;
        }

        // Default: open the GUI menu
        if (!player.hasPermission("serverfeatures.feature.glow.use")) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("general.no_permission").forAudience(player).build());
            return true;
        }

        GlowMenu.open(feature, player);
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
