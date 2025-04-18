package nl.hauntedmc.serverfeatures.features.glow.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.commands.FeatureCommand;
import nl.hauntedmc.serverfeatures.features.glow.Glow;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Command executor for the /glow command.
 */
public class GlowCommand extends FeatureCommand {
    private static final List<String> OPTIONS = new ArrayList<>();
    private final Glow feature;

    public GlowCommand(Glow feature) {
        super("glow");
        this.feature = feature;

        // Initialize tab-complete options with all NamedTextColor names
        for (NamedTextColor color : NamedTextColor.NAMES.values()) {
            OPTIONS.add(color.toString().toLowerCase());
        }
        // Include the "remove" option
        OPTIONS.add("remove");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(sender).build());
            return true;
        }
        Player player = (Player) sender;

        // Basic usage check
        if (args.length != 1) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("glow.usage").forAudience(sender).build());
            return true;
        }

        String arg = args[0];
        if (arg.equalsIgnoreCase("remove")) {
            // Remove glow
            boolean removed = feature.getGlowHandler().removeGlow(player);
            if (removed) {
                sender.sendMessage(feature.getLocalizationHandler().getMessage("glow.glow_removed").forAudience(sender).build());
            }
        } else {
            // Attempt to parse color
            NamedTextColor color;
            color = NamedTextColor.NAMES.value(arg.toLowerCase());
            if (color == null) {
                sender.sendMessage(feature.getLocalizationHandler().getMessage("glow.invalid_color").forAudience(sender).build());
                return true;
            }

            boolean success = feature.getGlowHandler().setGlow(player, color);
            if (success) {
                sender.sendMessage(feature.getLocalizationHandler().getMessage("glow.glow_set").forAudience(sender).withPlaceholders(Map.of("color", color.toString())).build());
            }
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender,
                                             @NotNull String alias,
                                             String @NotNull [] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            for (String option : OPTIONS) {
                if (option.startsWith(input)) {
                    suggestions.add(option);
                }
            }
            return suggestions;
        }
        return Collections.emptyList();
    }
}
