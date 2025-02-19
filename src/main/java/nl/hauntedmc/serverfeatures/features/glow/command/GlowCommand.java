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

/**
 * Command executor for the /glow command.
 */
public class GlowCommand extends FeatureCommand {
    private static List<String> OPTIONS;
    private final Glow feature;

    public GlowCommand(Glow feature) {
        super("glow");
        this.feature = feature;
        OPTIONS = new ArrayList<>();
        for (NamedTextColor color : NamedTextColor.NAMES.values()) {
            OPTIONS.add(color.toString().toLowerCase());
        }
        OPTIONS.add("remove");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        Player player = (Player) sender;
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /glow <color|remove>", NamedTextColor.YELLOW));
            return true;
        }
        String arg = args[0];
        if (arg.equalsIgnoreCase("remove")) {
            feature.removeGlow(player);
            sender.sendMessage(Component.text("Glow removed.", NamedTextColor.GREEN));
        } else {
            NamedTextColor color;
            try {
                color = NamedTextColor.NAMES.value(arg.toLowerCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(Component.text("Invalid color. Usage: /glow <color|remove>", NamedTextColor.RED));
                return true;
            }
            feature.setGlow(player, color);
            sender.sendMessage(
                    Component.text("Glow set to ", NamedTextColor.GREEN)
                            .append(Component.text(color.toString(), color))
            );
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull [] args) {
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
